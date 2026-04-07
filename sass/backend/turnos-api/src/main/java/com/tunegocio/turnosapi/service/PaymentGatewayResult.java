package com.tunegocio.turnosapi.service;

record PaymentGatewayResult(String externalReference, String checkoutUrl, String clientSecret) {
}
