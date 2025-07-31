package msg.api.chatbot.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.*;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import msg.api.chatbot.model.Message;
import msg.api.chatbot.repository.ConversationRepository;
import msg.api.chatbot.repository.MessageRepository;
import msg.api.chatbot.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAIService {

    @Autowired
    private QuoteRepository quoteRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;

    private final OpenAIClient client;

    public OpenAIService(@Value("${openai.api-key}") String apiKey) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public String sendMessage(List<ChatCompletionMessage> messages) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1);

        for (ChatCompletionMessage msg : messages) {
            builder.addMessage(msg);
        }

        ChatCompletionCreateParams params = builder.build();
        ChatCompletion chatCompletion = client.chat().completions().create(params);
        return chatCompletion.choices().get(0).message().content().orElse("Erro: Sem resposta.");
    }

    private ChatCompletionMessage buildMessage(String role, String content) {
        return ChatCompletionMessage.builder()
                .role(JsonValue.from(role))
                .content(content)
                .refusal(Optional.empty())
                .build();
    }


    public String analyzeDrawing(String fileId, int qtd) {
        System.out.println("[OpenAI] Iniciando análise do desenho com ID: " + fileId + " | Quantidade: " + qtd);
        List<ChatCompletionMessage> messages = new ArrayList<>();

        messages.add(buildMessage( "system", "Você é um especialista em análise de desenhos técnicos de peças industriais."));

        // 1. Pergunta sobre dimensões/formato
        String promptDimensoes = String.format("""
                Analise o desenho técnico em anexo (ID: %s).
                Quais medidas da matéria-prima preciso comprar pra fabricar essa peça?""", fileId);

        messages.add(buildMessage("user", promptDimensoes));
        String respostaDimensoes = sendMessage(messages);
        messages.add(buildMessage("assistant", respostaDimensoes));

        // 2. Pergunta sobre máquinas/processos
        String promptMaquinas = String.format("""
            Analise o desenho técnico em anexo (ID: %s).
            Quais processos de fabricação são necessários para fabricar a peça em anexo e por quê.
    
            Use exatamente os nomes da lista abaixo para "maquina":
            ["Torno Convencional", "Torno CNC", "Centro de Usinagem", "Fresadora Convencional", "Retifica Cilindrica", "Retifica Plana", "Eletroerosao", "Penetraçao", "Serra", "Eletroerosao a Fio"]
    
            Responda de forma explicativa.
            """, fileId);
        messages.add(buildMessage("user", promptMaquinas));
        String respostaMaquinas = sendMessage(messages);
        messages.add(buildMessage("assistant", respostaMaquinas));

        // 3. Estimar tempo com base nas respostas anteriores
        String promptTempo = String.format("""
            Analise o desenho técnico em anexo (ID: %s).
            Qual o tempo estimado para a produção de %s peças para cada máquina da resposta anterior?
            """, fileId, qtd);
        messages.add(buildMessage("user", promptTempo));
        String respostaTempo = sendMessage(messages);
        messages.add(buildMessage("assistant", respostaTempo));

        // 4. Geração do JSON final
        String promptResumo = String.format("""
            Com base nos textos abaixo, gere um JSON com as seguintes informações:
            {
              "material": se não encontrado deixar em branco
              "formato": string, um dos valores: "retangular", "redondo" ou "tubo"
              "dimensoes": objeto com os campos conforme o formato:
                  Se "retangular": inclua "espessura", "largura" e "comprimento" (números).
                  Se "redondo": inclua "diametro" e "comprimento" (números).
            "processos":
              "maquina": lista com os maquinas,.
              "hora": número com o tempo estimado em horas PARA CADA UMA das máquinas escolhidas separadamente
            }
            Só escreva o JSON, sem explicações ou comentários, e não comece o JSON com ```.
            """, respostaDimensoes, respostaMaquinas, respostaTempo);
        messages.add(buildMessage("user", promptResumo));
        String jsonResumo = sendMessage(messages);

        System.out.println("[OpenAI] JSON final gerado: " + jsonResumo);
        return respostaDimensoes;
    }

    public String classifyEmail(String subject, String body) {
        System.out.println("[OpenAI] Classificando e-mail - Assunto: " + subject);

        String userPrompt = String.format("""
            Analise o assunto e o corpo do e-mail e verifique se é de orçamento de produção, responda com um JSON válido contendo:
    
            {
              "is_orcamento": true/false,
              "quantidade": number,
              "material": str,
              "tratamento": str
            }
            
            Preencha os campos somente se for orçamento. Não adicione explicações fora do JSON nem comece o JSON com ```.
            
            ASSUNTO: %s
            CORPO DO E-MAIL:
            %s
            """, subject, body);

        List<ChatCompletionMessage> message = List.of(
                buildMessage("user", userPrompt));

        String resposta = sendMessage(message);
        System.out.println("[OpenAI] Resposta da classificação: " + resposta);
        return resposta;
    }

    public String fileUpload(File image) {
        System.out.println("[OpenAI] Realizando upload do arquivo: " + image.getAbsolutePath());
        FileCreateParams params = FileCreateParams.builder()
                .purpose(FilePurpose.USER_DATA)
                .file(image.toPath())
                .build();
        FileObject fileObject = client.files().create(params);
        System.out.println("[OpenAI] Upload finalizado. File ID: " + fileObject.id());
        return fileObject.id();
    }

    // Busca mensagens pelo ID da comida
//    public List<Message> getMessagesByQuoteId(Long quoteId) {
//        return conversationRepository.findByQuoteId(quoteId)
//                .map(conversation -> messageRepository.findByConversationId(conversation.getId()))
//                .orElseGet(List::of);
//    }
//
//    // Adiciona uma nova mensagem, envia para o modelo e salva resposta
//    public Message addMessage(Long quoteId, String role, String content) {
//        // Pega ou cria a conversa
//        var conversation = conversationRepository.findByQuoteId(quoteId).orElseGet(() -> {
//            var quote = quoteRepository.findById(quoteId)
//                    .orElseThrow(() -> new IllegalArgumentException("Comida não encontrada"));
//            var conv = new msg.api.chatbot.model.Conversation();
//            conv.setQuote(quote);
//            return conversationRepository.save(conv);
//        });
//
//        // Salva mensagem do usuário
//        Message userMessage = new Message();
//        userMessage.setRole(role);
//        userMessage.setContent(content);
//        userMessage.setConversation(conversation);
//        messageRepository.save(userMessage);
//
//        // Envia para o modelo
//        String response = sendMessage(null, content);
//
//        // Salva resposta do assistente
//        Message assistantMessage = new Message();
//        assistantMessage.setRole("assistant");
//        assistantMessage.setContent(response);
//        assistantMessage.setConversation(conversation);
//        messageRepository.save(assistantMessage);
//
//        return assistantMessage;
//    }
}