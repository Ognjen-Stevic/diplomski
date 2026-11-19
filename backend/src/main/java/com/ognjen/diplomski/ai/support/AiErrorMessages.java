package com.ognjen.diplomski.ai.support;

/** Produces bounded error text suitable for durable AI lifecycle records. */
public final class AiErrorMessages {
  private AiErrorMessages() {}

  public static String safe(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) return "Interna greška AI obrade.";
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
