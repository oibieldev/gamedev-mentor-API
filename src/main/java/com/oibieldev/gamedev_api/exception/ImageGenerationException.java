package com.oibieldev.gamedev_api.exception;

import com.oibieldev.gamedev_api.dto.image.ImageProviderDiagnostics;

public class ImageGenerationException extends RuntimeException {

    public enum Reason {
        RATE_LIMITED, QUOTA_UNAVAILABLE, QUOTA_EXHAUSTED, BILLING_REQUIRED, CREDITS_EXHAUSTED,
        AUTHENTICATION_FAILED, ACCESS_DENIED, MODEL_NOT_FOUND, API_DISABLED,
        UNAVAILABLE, TIMEOUT, INVALID_RESPONSE, BLOCKED
    }

    private final Reason reason;
    private final ImageProviderDiagnostics diagnostics;

    public ImageGenerationException(Reason reason) {
        this(reason, null);
    }

    public ImageGenerationException(Reason reason, Throwable cause) {
        this(reason, cause, null);
    }

    public ImageGenerationException(Reason reason, Throwable cause, ImageProviderDiagnostics diagnostics) {
        super(buildMessage(reason, diagnostics), cause);
        this.reason = reason;
        this.diagnostics = diagnostics;
    }

    private static String buildMessage(Reason reason, ImageProviderDiagnostics diagnostics) {
        String message = switch (reason) {
            case RATE_LIMITED -> "O provedor retornou HTTP 429 por restrição de cota ou capacidade. "
                    + "Confira as cotas e o faturamento do projeto da chave; isso não confirma que todo o limite foi consumido.";
            case QUOTA_UNAVAILABLE -> "O provedor informou cota zero para a geração de imagens. "
                    + "Confira o projeto da chave, a ativação do plano pago e a cota disponível para o modelo.";
            case QUOTA_EXHAUSTED -> "O provedor informou que uma cota de uso foi atingida. "
                    + "Consulte a cota indicada no diagnóstico e sua janela de renovação no AI Studio.";
            case BILLING_REQUIRED -> "O provedor informou que o faturamento está desativado. "
                    + "Ative-o no projeto associado à chave utilizada pela aplicação.";
            case CREDITS_EXHAUSTED -> "O provedor informou que o saldo pré-pago está esgotado. "
                    + "Confira os créditos e a confirmação do pagamento no AI Studio.";
            case AUTHENTICATION_FAILED -> "O provedor rejeitou a autenticação. "
                    + "Confira GEMINI_API_KEY e reinicie a aplicação se a chave foi alterada.";
            case ACCESS_DENIED -> "O provedor negou acesso à geração de imagens. "
                    + "Confira as permissões e restrições da chave no projeto.";
            case MODEL_NOT_FOUND -> "O modelo ou a operação de geração de imagens não foi encontrado. "
                    + "Confira GEMINI_IMAGE_MODEL e a disponibilidade do modelo para o projeto.";
            case API_DISABLED -> "O provedor informou que a API está desativada no projeto da chave. "
                    + "Confira a ativação da Generative Language API no Google Cloud.";
            case UNAVAILABLE -> "O provedor de imagens está indisponível.";
            case TIMEOUT -> "O provedor de imagens excedeu o tempo de resposta.";
            case INVALID_RESPONSE -> "O provedor não retornou uma imagem válida.";
            case BLOCKED -> "A geração de imagem foi bloqueada pelo provedor.";
        };
        if (diagnostics != null && diagnostics.hasFreeTierQuota()) {
            message += " A resposta do Google cita uma cota do plano gratuito. "
                    + "Confirme que a aplicação usa uma chave do projeto em que o faturamento foi ativado.";
        }
        if (reason == Reason.RATE_LIMITED && diagnostics != null
                && diagnostics.retryAfterSeconds() != null && diagnostics.retryAfterSeconds() > 0) {
            message += " O provedor sugere aguardar pelo menos " + diagnostics.retryAfterSeconds()
                    + " segundos antes de outra tentativa; isso não garante a liberação da cota.";
        }
        return message;
    }

    public Reason getReason() {
        return reason;
    }

    public ImageProviderDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
