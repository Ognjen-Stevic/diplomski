package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.NotNull;

public record RequisitionEmailRequest(
        @NotNull RequisitionEmailRecipient recipient
) {
}
