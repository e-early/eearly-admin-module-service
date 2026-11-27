-- Optional Basic Auth credentials for the algorithm's own service endpoint. The password column holds the
-- AES-GCM ciphertext of the password (see algorithm.credentials.encryption-key), never the password itself.
ALTER TABLE t_algorithm
    ADD COLUMN auth_username VARCHAR(255),
    ADD COLUMN auth_password TEXT;
