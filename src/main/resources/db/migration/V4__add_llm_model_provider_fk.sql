ALTER TABLE llm_model_config
    ADD CONSTRAINT fk_llm_model_provider
        FOREIGN KEY (provider_id)
        REFERENCES llm_provider_config (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;
