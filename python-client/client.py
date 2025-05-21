import grpc
import server_pb2
import server_pb2_grpc
import uuid
import sys
import logging
import os
from google.protobuf import empty_pb2 # Necessário para chamadas sem argumentos, se houver

# Configuração do logger (mantida)
log_formatter = logging.Formatter('%(asctime)s - %(levelname)s - Client - %(message)s')
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO) 

# Limpa handlers existentes para evitar duplicação se o script for re-executado
if logger.hasHandlers():
    logger.handlers.clear()

# Handler para arquivo
log_dir = os.path.dirname(os.path.abspath(__file__)) 
log_file = os.path.join(log_dir, 'client.log')
file_handler = logging.FileHandler(log_file, mode='a') # Usar append mode
file_handler.setFormatter(log_formatter)
logger.addHandler(file_handler)

# Handler para console 
console_handler = logging.StreamHandler(sys.stdout)
console_handler.setFormatter(log_formatter)
logger.addHandler(console_handler)

# --- Funções Auxiliares ---

def create_user(stub, name_prefix):
    """Tenta criar um usuário com um nome único e retorna seu ID."""
    user_name_to_create = f"{name_prefix}_{uuid.uuid4().hex[:6]}"
    logger.info(f"--- Tentando criar usuário: {user_name_to_create} ---")
    try:
        create_request = server_pb2.CreateUserRequest(name=user_name_to_create)
        # Aumentar timeout pode ser útil se o coordenador estiver ocupado
        create_response = stub.CreateUserRPC(create_request, timeout=15) 
        if create_response and create_response.id:
            logger.info(f"SUCESSO: Usuário '{create_response.name}' criado com ID: {create_response.id}")
            return create_response.id, create_response.name # Retorna ID e Nome
        else:
            logger.warning(f"FALHA: Não foi possível criar o usuário '{user_name_to_create}'. Resposta: {create_response}")
            return None, None
    except grpc.RpcError as e:
        logger.error(f"FALHA: Erro gRPC durante criação do usuário '{user_name_to_create}': {e.code()} - {e.details()}")
        return None, None
    except Exception as e:
         logger.exception(f"FALHA: Erro inesperado durante criação do usuário '{user_name_to_create}': {e}")
         return None, None

def follow_user(stub, follower_id, followed_id):
    """Tenta fazer um usuário seguir outro."""
    logger.info(f"--- Tentando fazer {follower_id} seguir {followed_id} ---")
    if not follower_id or not followed_id:
        logger.error("FALHA: IDs de seguidor ou seguido inválidos para seguir.")
        return False
    try:
        follow_request = server_pb2.FollowRequest(followerId=follower_id, followedId=followed_id)
        follow_response = stub.FollowUserRPC(follow_request, timeout=10)
        if follow_response and follow_response.success:
            logger.info(f"SUCESSO: Requisição para seguir processada. Mensagem: {follow_response.message}")
            return True
        else:
            logger.warning(f"FALHA: Requisição para seguir falhou. Mensagem: {follow_response.message if follow_response else 'Sem resposta'}")
            return False
    except grpc.RpcError as e:
        logger.error(f"FALHA: Erro gRPC durante requisição para seguir: {e.code()} - {e.details()}")
        return False
    except Exception as e:
        logger.exception(f"FALHA: Erro inesperado durante requisição para seguir: {e}")
        return False

def create_post(stub, user_id, user_name, content):
    """Tenta criar um post para um usuário."""
    logger.info(f"--- Usuário {user_name} ({user_id}) tentando postar: '{content[:30]}...' ---")
    if not user_id:
        logger.error("FALHA: ID de usuário inválido para postar.")
        return False
    try:
        post_request = server_pb2.CreatePostRequest(user_id=user_id, content=content)
        post_response = stub.CreatePostRPC(post_request, timeout=15)
        if post_response and post_response.post_info and post_response.post_info.id:
            logger.info(f"SUCESSO: Post criado por {user_name}. ID do Post: {post_response.post_info.id}")
            return True
        else:
            logger.warning(f"FALHA: Não foi possível criar post para {user_name}. Resposta: {post_response}")
            return False
    except grpc.RpcError as e:
        logger.error(f"FALHA: Erro gRPC durante criação de post por {user_name}: {e.code()} - {e.details()}")
        return False
    except Exception as e:
        logger.exception(f"FALHA: Erro inesperado durante criação de post por {user_name}: {e}")
        return False

def send_message(stub, sender_id, sender_name, receiver_id, receiver_name, content):
    """Tenta enviar uma mensagem privada."""
    logger.info(f"--- {sender_name} ({sender_id}) tentando enviar msg para {receiver_name} ({receiver_id}): '{content[:30]}...' ---")
    if not sender_id or not receiver_id:
         logger.error("FALHA: IDs de remetente ou destinatário inválidos.")
         return False
    try:
        message_request = server_pb2.SendMessageRequest(sender_id=sender_id, receiver_id=receiver_id, content=content)
        message_response = stub.SendMessageRPC(message_request, timeout=15)
        if message_response and message_response.message_info and message_response.message_info.id:
            logger.info(f"SUCESSO: Mensagem de {sender_name} para {receiver_name} enviada. ID da Msg: {message_response.message_info.id}")
            return True
        else:
            logger.warning(f"FALHA: Não foi possível enviar mensagem de {sender_name} para {receiver_name}. Resposta: {message_response}")
            return False
    except grpc.RpcError as e:
        logger.error(f"FALHA: Erro gRPC durante envio de mensagem de {sender_name} para {receiver_name}: {e.code()} - {e.details()}")
        return False
    except Exception as e:
        logger.exception(f"FALHA: Erro inesperado durante envio de mensagem de {sender_name} para {receiver_name}: {e}")
        return False

# --- Função Principal ---

def run(target_server='localhost:9090'):
    logger.info(f"Iniciando cliente para interagir com {target_server}...")
    try:
        # Usar 'with' garante que o canal será fechado
        with grpc.insecure_channel(target_server) as channel:
            logger.info("Canal gRPC criado.")
            stub = server_pb2_grpc.ServerServiceStub(channel)
            logger.info("Stub gRPC criado.")

            # 1. Criar usuários
            users_data = []
            user_name_stems = ["Alfa", "Bravo", "Charlie", "Delta", "Echo"] # 5 usuários
            for i in range(5):
                # Usar um nome base e adicionar o stem para variedade
                user_id, user_name = create_user(stub, f"Usuario_Py_{user_name_stems[i]}")
                if user_id and user_name:
                    users_data.append({"id": user_id, "name": user_name})
                else:
                    logger.warning(f"Falha ao tentar criar o usuário com prefixo 'Usuario_Py_{user_name_stems[i]}'.")
                    # A verificação len(users_data) == 5 cuidará do aborto se necessário.

            # Prosseguir apenas se TODOS os 5 usuários foram criados
            if len(users_data) == 5:
                logger.info(f"*** Todos os {len(users_data)} usuários foram criados com sucesso: ***")
                for user_idx, user_info in enumerate(users_data): 
                    logger.info(f" - Usuário {user_idx+1} ({user_info['name']}): ID {user_info['id']}")

                # 2. Seguir: Cada usuário segue todos os outros
                logger.info("--- Iniciando fase de seguir usuários (todos seguem todos) ---")
                for i in range(len(users_data)):
                    for j in range(len(users_data)):
                        if i == j:  # Usuário não segue a si mesmo
                            continue
                        # A função follow_user já loga internamente sucesso/falha da ação específica
                        follow_user(stub, users_data[i]['id'], users_data[j]['id'])

                # 3. Criar Posts: Cada usuário cria um post
                logger.info("--- Iniciando fase de criação de posts (um por usuário) ---")
                for user_data in users_data:
                    create_post(stub, user_data['id'], user_data['name'],
                                f"Olá comunidade! Sou {user_data['name']}. Este é meu primeiro post através do cliente Python!")

                # 4. Trocar Mensagens
                logger.info("--- Iniciando fase de troca de mensagens ---")
                
                # Cenário A: Mensagens em cadeia (User_0 -> User_1, ..., User_4 -> User_0)
                logger.info("Enviando mensagens em cadeia...")
                for i in range(len(users_data)):
                    sender = users_data[i]
                    receiver_index = (i + 1) % len(users_data) 
                    receiver = users_data[receiver_index]
                    send_message(stub, sender['id'], sender['name'], receiver['id'], receiver['name'],
                                 f"Olá {receiver['name']}, tudo bem por aí? De: {sender['name']}.")

                # Cenário B: Algumas mensagens diretas específicas
                logger.info("Enviando mensagens diretas adicionais...")
                # User_Alfa (índice 0) para User_Delta (índice 3)
                send_message(stub, users_data[0]['id'], users_data[0]['name'],
                             users_data[3]['id'], users_data[3]['name'],
                             f"E aí, {users_data[3]['name']}! Uma mensagem especial de {users_data[0]['name']}.")
                
                # User_Charlie (índice 2) para User_Echo (índice 4)
                send_message(stub, users_data[2]['id'], users_data[2]['name'],
                             users_data[4]['id'], users_data[4]['name'],
                             f"Oi {users_data[4]['name']}, {users_data[2]['name']} aqui, só para constar!")
                
                logger.info("--- Fim da fase de troca de mensagens ---")

            else:
                created_count = len(users_data)
                logger.error(f"FALHA GERAL NA CRIAÇÃO DE USUÁRIOS: Não foi possível criar todos os 5 usuários. "
                             f"Apenas {created_count} foram criados. Abortando interações principais.")

    except grpc.RpcError as e:
        logger.error(f"Não foi possível conectar ou erro gRPC geral: {e.code()} - {e.details()}")
    except Exception as e:
        logger.exception(f"Ocorreu um erro inesperado na execução principal: {e}")

    logger.info(f"Execução do cliente finalizada para {target_server}.")

if __name__ == '__main__':
    # Logger já configurado globalmente
    target = 'localhost:9090' # Padrão
    # Permite especificar o alvo via argumento de linha de comando
    # Ex: python client.py localhost:9091
    if len(sys.argv) > 1:
        target = sys.argv[1]
        logger.info(f"Alvo especificado via argumento: {target}")
    else:
        logger.info(f"Usando alvo padrão: {target}")
        
    run(target)
