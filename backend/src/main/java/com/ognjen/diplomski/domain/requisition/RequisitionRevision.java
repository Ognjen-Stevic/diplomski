package com.ognjen.diplomski.domain.requisition;

import com.ognjen.diplomski.entity.Requisition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;

/** Opaque precondition for mutable requisition content, not a historical product snapshot. */
public final class RequisitionRevision {
    private RequisitionRevision() {}

    public static String of(Requisition requisition) {
        StringBuilder value = new StringBuilder();
        append(value, requisition.getId());
        append(value, requisition.getStatus());
        append(value, requisition.getStatusNote());
        append(value, requisition.getSentAt());
        append(value, requisition.getGratisReason());
        append(value, requisition.getCustomReason());
        append(value, requisition.getNote());
        requisition.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> {
                    append(value, item.getProduct().getId());
                    append(value, item.getQuantity().stripTrailingZeros().toPlainString());
                    append(value, item.getStimulationPerUnit());
                });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void append(StringBuilder result, Object value) {
        if (value == null) {
            result.append("-1:");
            return;
        }
        String text = String.valueOf(value);
        result.append(text.length()).append(':').append(text);
    }
}
