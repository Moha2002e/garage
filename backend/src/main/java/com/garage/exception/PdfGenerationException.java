package com.garage.exception;

/** The PDF could not be rendered. Carries no cause and no data: the handler answers a neutral 500. */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException() {
        super("The PDF could not be generated");
    }
}
