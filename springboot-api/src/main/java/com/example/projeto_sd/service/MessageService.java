package com.example.projeto_sd.service;

import com.example.projeto_sd.dto.message.CreateMessageRequestDTO;
import com.example.projeto_sd.dto.message.MessageDTO;
import com.example.projeto_sd.model.Message;
import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.model.User;
import com.example.projeto_sd.repository.MessageRepository;
import com.example.projeto_sd.repository.ServerRepository;
import com.example.projeto_sd.repository.UserRepository;
import com.example.projeto_sd.exception.UserNotFoundException;

import com.example.projeto_sd.grpc.ServerCommsProto.MessageInfo;
import com.example.projeto_sd.grpc.ServerCommsProto.SendMessageRequest;
import com.example.projeto_sd.grpc.ServerCommsProto.SendMessageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.Comparator;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private GrpcClientService grpcClientService;

    @Autowired
    private LogicalClock logicalClock;

    @Autowired
    private ElectionService electionService;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${server.id}") // Injetar o ID do servidor atual
    private String selfServerId;

    @Value("${spring.application.name}")
    private String selfServiceName;

    /**
     * Processa o envio de uma nova mensagem.
     * Verifica se é coordenador, se sim, processa localmente e replica.
     * Se não, encaminha para o coordenador.
     *
     * @param requestDTO DTO contendo informações da mensagem.
     * @return DTO da mensagem criada.
     */
    @Transactional // Necessário para salvar localmente ou interagir com gRPC
    public MessageDTO sendMessage(CreateMessageRequestDTO requestDTO) {
        boolean isCoordinator = electionService.isCoordinator();

        if (isCoordinator) {
            log.info("[SendMessage] Nó {} é COORDENADOR. Processando localmente e iniciando replicação.", selfServerId);
            return processAndReplicateMessage(requestDTO);
        } else {
            log.info("[SendMessage] Nó {} é SEGUIDOR. Encaminhando requisição para o coordenador.", selfServerId);

            // Obter detalhes do coordenador
            Optional<String> coordinatorIdOpt = electionService.getCoordinatorId();
            if (coordinatorIdOpt.isEmpty()) {
                log.error("[SendMessage-Forward] ID do Coordenador desconhecido. Não é possível encaminhar a mensagem.");
                throw new RuntimeException("Coordenador não disponível para processar a requisição de envio de mensagem.");
            }
            String coordinatorId = coordinatorIdOpt.get();

            Optional<Server> coordinatorServerOpt = electionService.getCoordinatorServerDetails(coordinatorId);
             if (coordinatorServerOpt.isEmpty()) {
                 log.error("[SendMessage-Forward] Detalhes do coordenador {} não encontrados. Não é possível encaminhar a mensagem.", coordinatorId);
                 throw new RuntimeException("Detalhes do coordenador não encontrados. Não é possível encaminhar a mensagem.");
            }

            Server coordinator = coordinatorServerOpt.get();

            SendMessageRequest protoRequest = SendMessageRequest.newBuilder()
                    .setSenderId(requestDTO.getSenderId())
                    .setReceiverId(requestDTO.getReceiverId())
                    .setContent(requestDTO.getContent())
                    .build();

            log.info("[SendMessage-Forward] Encaminhando mensagem de {} para {} via coordenador {} em {}:{}",
                     requestDTO.getSenderId(), requestDTO.getReceiverId(), coordinator.getId(), coordinator.getHost(), coordinator.getPort());

            try {
                SendMessageResponse protoResponse = grpcClientService.forwardSendMessageRPC(
                        coordinator.getHost(),
                        coordinator.getPort(),
                        protoRequest
                );

                // Converter resposta do proto para DTO
                log.info("[SendMessage-Forward] Resposta recebida do coordenador.");
                return convertProtoToMessageDTO(protoResponse.getMessageInfo());
            } catch (Exception e) {
                log.error("[SendMessage-Forward] Erro ao encaminhar requisição de envio de mensagem para o coordenador {}: {}", coordinatorId, e.getMessage(), e);
                throw new RuntimeException("Falha ao encaminhar requisição de envio de mensagem para o coordenador.", e);
            }
        }
    }

    /**
     * (Coordenador) Cria a mensagem localmente, atribui relógio lógico e replica para seguidores.
     * @param requestDTO O DTO da requisição (CreateMessageRequestDTO).
     * @return O DTO da mensagem criada (MessageDTO).
     */
    private MessageDTO processAndReplicateMessage(CreateMessageRequestDTO requestDTO) { // Nome do DTO corrigido
        logicalClock.increment();
        
        log.info("[SendMessage-Coord] Processando mensagem de {} para {}. Relógio atual: {}",
                 requestDTO.getSenderId(), requestDTO.getReceiverId(), logicalClock.getValue());

        User sender = userRepository.findById(requestDTO.getSenderId())
                .orElseThrow(() -> new UserNotFoundException("Remetente não encontrado com ID: " + requestDTO.getSenderId()));
        User receiver = userRepository.findById(requestDTO.getReceiverId())
                .orElseThrow(() -> new UserNotFoundException("Destinatário não encontrado com ID: " + requestDTO.getReceiverId()));

        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(requestDTO.getContent());
        message.setRead(false);
        
        message.setLogicalClock(logicalClock.getValue());
        message.setSentAt(LocalDateTime.now());

        Server originServerEntity = serverRepository.findById(this.selfServerId)
            .orElseThrow(() -> new IllegalStateException("Entidade do servidor atual com ID " + this.selfServerId + " não encontrada no banco de dados."));
        
        message.setServer(originServerEntity);

        Message savedMessage = messageRepository.save(message);
        log.info("[SendMessage-Coord] Mensagem salva localmente com ID: {}. Relógio: {}", savedMessage.getId(), savedMessage.getLogicalClock());

        MessageInfo messageInfoProto = convertEntityToProto(savedMessage);

        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfServerId) && !"unknown".startsWith(peerServerId) && getGrpcPortFromInstance(instance) != -1;
                })
                .collect(Collectors.toList());

        if (!activePeerInstances.isEmpty()) {
             log.info("[SendMessage-Coord] Iniciando replicação da mensagem {} para {} peers ativos via Consul.", savedMessage.getId(), activePeerInstances.size());
             activePeerInstances.forEach(peerInstance -> {
                String peerHost = peerInstance.getHost();
                int peerPort = getGrpcPortFromInstance(peerInstance);
                String peerServerId = getServerIdFromInstance(peerInstance);

                try {
                    log.debug("[SendMessage-Coord] Replicando mensagem {} para o peer {} em {}:{}",
                              savedMessage.getId(), peerServerId, peerHost, peerPort);
                    grpcClientService.replicateMessageToPeer(peerHost, peerPort, messageInfoProto);
                } catch (Exception e) {
                    log.error("[SendMessage-Coord] Erro ao iniciar replicação da mensagem {} para o peer {} ({}:{}): {}",
                              savedMessage.getId(), peerServerId, peerHost, peerPort, e.getMessage(), e);
                }
             });
             log.info("[SendMessage-Coord] Finalizada a iniciação do processo de replicação para a mensagem {}.", savedMessage.getId());
        } else {
             log.info("[SendMessage-Coord] Nenhum peer ativo encontrado via Consul para replicação da mensagem {}.", savedMessage.getId());
        }

        log.debug("[SendMessage-Coord] Mensagem processada e replicação iniciada. Retornando MessageDTO.");
        return convertEntityToDTO(savedMessage);
    }

    /**
     * Converte uma entidade Message JPA para MessageInfo Protobuf (ServerCommsProto).
     *
     * @param message A entidade Message.
     * @return O objeto MessageInfo Protobuf.
     */
    private MessageInfo convertEntityToProto(Message message) {
        if (message.getSentAt() == null) {
            log.error("CRÍTICO: sentAt é nulo para a Mensagem ID {} mesmo após definição explícita e salvamento!", message.getId());
            throw new IllegalStateException("Timestamp sentAt da mensagem é nulo durante a conversão para proto.");
        }
        return MessageInfo.newBuilder()
                .setId(message.getId())
                .setSenderId(message.getSender().getId())
                .setReceiverId(message.getReceiver().getId())
                .setContent(message.getContent())
                .setSentAtMillis(message.getSentAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .setIsRead(message.isRead())
                .setLogicalClock(message.getLogicalClock())
                .setOriginServerId(message.getServer().getId())
                .build();
    }

     /**
     * Converte um MessageInfo Protobuf (ServerCommsProto) para um MessageDTO.
     *
     * @param messageInfo O objeto MessageInfo Protobuf.
     * @return O objeto MessageDTO.
     */
    private MessageDTO convertProtoToMessageDTO(MessageInfo messageInfo) {
        return new MessageDTO(
            messageInfo.getId(),
            messageInfo.getSenderId(),
            messageInfo.getReceiverId(),
            messageInfo.getContent(),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(messageInfo.getSentAtMillis()), ZoneId.systemDefault()),
            messageInfo.getLogicalClock(),
            messageInfo.getIsRead()
        );
    }

    /**
     * Converte uma entidade Message JPA para MessageDTO.
     * @param message A entidade Message.
     * @return O objeto MessageDTO.
     */
     private MessageDTO convertEntityToDTO(Message message) {
         return new MessageDTO(
             message.getId(),
             message.getSender().getId(),
             message.getReceiver().getId(),
             message.getContent(),
             message.getSentAt(),
             message.getLogicalClock(),
             message.isRead()
         );
     }

    /**
     * Busca as mensagens trocadas entre dois usuários (conversa).
     *
     * @param userId1 ID do primeiro usuário.
     * @param userId2 ID do segundo usuário.
     * @param pageable Objeto de paginação.
     * @return Página de MessageDTOs.
     */
    @Transactional(readOnly = true)
    public Page<MessageDTO> getConversation(String userId1, String userId2, Pageable pageable) {
        log.debug("Buscando conversa entre {} e {} com paginação {}", userId1, userId2, pageable);

        
        if (!userRepository.existsById(userId1)) {
            throw new UserNotFoundException("Usuário não encontrado com ID: " + userId1);
        }
         if (!userRepository.existsById(userId2)) {
            throw new UserNotFoundException("Usuário não encontrado com ID: " + userId2);
        }

        Page<Message> messagePage = messageRepository.findConversation(userId1, userId2, pageable);

        return messagePage.map(this::convertEntityToDTO);
    }

    public List<MessageDTO> getMessagesForUser(String userId) {
        logicalClock.increment();
        
        log.info("Buscando mensagens para o usuário {}. Relógio atual: {}", userId, logicalClock.getValue());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado com ID: " + userId));

        
        List<Message> messages = messageRepository.findByReceiver(user);

        messages.sort(Comparator.comparing(Message::getSentAt));


        return messages.stream()
                .map(this::convertEntityToDTO)
                .collect(Collectors.toList());
    }
    
     public List<MessageDTO> getAllMessages() {
        logicalClock.increment();
        
        log.info("Buscando todas as mensagens. Relógio atual: {}", logicalClock.getValue());

        List<Message> messages = messageRepository.findAll();

        return messages.stream()
                .map(this::convertEntityToDTO)
                .collect(Collectors.toList());
    }

    // Método chamado pelo gRPC Server (ServerServiceImpl) quando recebe uma mensagem replicada
    // Renomeado de processReplicatedMessage para saveReplicatedMessage
    @Transactional
    public void saveReplicatedMessage(MessageInfo messageInfo) {
        logicalClock.synchronizeWith(messageInfo.getLogicalClock());
        log.info("[Replicação] Processando mensagem replicada ID: {}. Relógio recebido: {}. Relógio atualizado: {}",
                 messageInfo.getId(), messageInfo.getLogicalClock(), logicalClock.getValue());

        Optional<Message> existingMessage = messageRepository.findById(messageInfo.getId());
        if (existingMessage.isPresent()) {
            log.warn("[Replicação] Mensagem com ID {} já existe. Pulando processamento da replicação.", messageInfo.getId());
            return;
        }

        User sender = userRepository.findById(messageInfo.getSenderId())
                .orElseThrow(() -> new UserNotFoundException("Remetente não encontrado para mensagem replicada: " + messageInfo.getSenderId()));
        User receiver = userRepository.findById(messageInfo.getReceiverId())
                .orElseThrow(() -> new UserNotFoundException("Destinatário não encontrado para mensagem replicada: " + messageInfo.getReceiverId()));

        String originServerId = messageInfo.getOriginServerId();
        Server originServer = serverRepository.findById(originServerId)
                .orElseThrow(() -> new IllegalStateException("Servidor de origem com ID " + originServerId + " não encontrado para mensagem replicada " + messageInfo.getId()));

        Message message = new Message();
        message.setId(messageInfo.getId());
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(messageInfo.getContent());
        message.setSentAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(messageInfo.getSentAtMillis()), ZoneId.systemDefault()));
        message.setRead(messageInfo.getIsRead());
        message.setLogicalClock(messageInfo.getLogicalClock());
        message.setServer(originServer);

        messageRepository.save(message);
        log.info("[Replicação] Mensagem replicada ID: {} salva com sucesso. Relógio: {}. Associada ao servidor de origem: {}", message.getId(), message.getLogicalClock(), originServerId);
    }

    private int getGrpcPortFromInstance(ServiceInstance instance) {
        String grpcPortStr = instance.getMetadata().get("gRPC_port");
        if (grpcPortStr == null) {
             log.warn("Metadado 'gRPC_port' não encontrado para a instância {}. Usando porta padrão ou retornando -1.", instance.getInstanceId());
             return -1; 
        }
        try {
            return Integer.parseInt(grpcPortStr);
        } catch (NumberFormatException e) {
            log.warn("Não foi possível converter o metadata gRPC_port ('{}') para a instância {}. Erro: {}", grpcPortStr, instance.getInstanceId(), e.getMessage());
            return -1;
        }
    }

    private String getServerIdFromInstance(ServiceInstance instance) {
        String serverId = instance.getMetadata().get("server-id");
        if (serverId == null || serverId.trim().isEmpty()) {
             log.warn("Não foi possível encontrar ou metadado 'server-id' está vazio para a instância {}.", instance.getInstanceId());
             return "unknown-" + instance.getInstanceId(); 
        }
        return serverId.trim();
    }
}