ALTER TABLE rag_unit
    ADD FULLTEXT INDEX ft_rag_unit_keyword (title, content, filename) WITH PARSER ngram;
