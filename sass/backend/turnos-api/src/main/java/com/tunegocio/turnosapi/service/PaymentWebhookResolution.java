package com.tunegocio.turnosapi.service;

record PaymentWebhookResolution(String tenantSlug, Long pedidoId, String providerPaymentId) {
}
