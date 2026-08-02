CREATE TABLE booking_documents (
    id                 BIGSERIAL PRIMARY KEY,
    booking_id         BIGINT NOT NULL,
    client_id          BIGINT NOT NULL,
    lawyer_id          BIGINT NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT NOT NULL,
    storage_path       VARCHAR(500) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    uploaded_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_documents_booking_id ON booking_documents(booking_id);