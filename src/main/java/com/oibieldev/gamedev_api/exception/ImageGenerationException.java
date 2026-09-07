package com.oibieldev.gamedev_api.exception;

public class ImageGenerationException extends RuntimeException {

    public enum Reason {
        RATE_LIMITED, UNAVAILABLE, TIMEOUT, INVALID_RESPONSE, BLOCKED
    }

    private final Reason reason;

    public ImageGenerationException(Reason reason) {
        this(reason, null);
    }

    public ImageGenerationException(Reason reason, Throwable cause) {
        super(switch (reason) {
            case RATE_LIMITED -> "O limite de geração de imagens foi atingido.";
            case UNAVAILABLE -> "O provedor de imagens está indisponível.";
            case TIMEOUT -> "O provedor de imagens excedeu o tempo de resposta.";
            case INVALID_RESPONSE -> "O provedor não retornou uma imagem válida.";
            case BLOCKED -> "A geração de imagem foi bloqueada pelo provedor.";
        }, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
