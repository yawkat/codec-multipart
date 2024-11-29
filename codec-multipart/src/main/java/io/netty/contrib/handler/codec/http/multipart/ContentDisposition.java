package io.netty.contrib.handler.codec.http.multipart;

import io.netty5.handler.codec.http.HttpHeaderValues;

/**
 * Parsed representation of the {@code Content-Disposition} header, giving access to the file name.
 */
public final class ContentDisposition extends ParmParser implements ParsedHeaderValue {
    private final String headerValue;

    private boolean parsed;
    private String name;
    private String filename;

    ContentDisposition(String headerValue) {
        this.headerValue = headerValue;
    }

    private void parse() {
        if (!parsed) {
            run(headerValue);
            parsed = true;
        }
    }

    /**
     * The field name specified in this header.
     *
     * @return The name, or {@code null} if not given
     */
    public String name() {
        parse();
        return name;
    }

    /**
     * The file name specified in this header.
     *
     * @return The file name, or {@code null} if not given
     */
    public String fileName() {
        parse();
        return filename;
    }

    @Override
    void visitType(String type) {
    }

    @Override
    boolean decodeExtendedAttribute(String attribute) {
        return true;
    }

    @Override
    boolean visitAttribute(String attribute) {
        return HttpHeaderValues.FILENAME.contentEqualsIgnoreCase(attribute) || HttpHeaderValues.NAME.contentEqualsIgnoreCase(attribute);
    }

    @Override
    void visitAttributeValue(String attribute, String value) {
        if (HttpHeaderValues.FILENAME.contentEqualsIgnoreCase(attribute)) {
            filename = value;
        } else {
            assert HttpHeaderValues.NAME.contentEqualsIgnoreCase(attribute);
            name = value;
        }
    }
}
