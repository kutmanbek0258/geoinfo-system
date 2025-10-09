--
-- Name: documents; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA documents;


ALTER SCHEMA documents OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: document_tag_link; Type: TABLE; Schema: documents; Owner: postgres
--

CREATE TABLE documents.document_tag_link (
    document_id uuid NOT NULL,
    tag_id bigint NOT NULL
);


ALTER TABLE documents.document_tag_link OWNER TO postgres;

--
-- Name: documents; Type: TABLE; Schema: documents; Owner: postgres
--

CREATE TABLE documents.documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    geo_object_id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    minio_object_key character varying(255) NOT NULL,
    mime_type character varying(100) NOT NULL,
    file_size_bytes bigint NOT NULL,
    description text,
    is_latest_version boolean DEFAULT true,
    created_by character varying(255),
    created_date timestamp without time zone,
    last_modified_by character varying(255),
    last_modified_date timestamp without time zone
);


ALTER TABLE documents.documents OWNER TO postgres;

--
-- Name: tags; Type: TABLE; Schema: documents; Owner: postgres
--

CREATE TABLE documents.tags (
    id bigint NOT NULL,
    name character varying(50) NOT NULL
);


ALTER TABLE documents.tags OWNER TO postgres;

--
-- Name: tags_id_seq; Type: SEQUENCE; Schema: documents; Owner: postgres
--

CREATE SEQUENCE documents.tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE documents.tags_id_seq OWNER TO postgres;

--
-- Name: tags_id_seq; Type: SEQUENCE OWNED BY; Schema: documents; Owner: postgres
--

ALTER SEQUENCE documents.tags_id_seq OWNED BY documents.tags.id;


--
-- Name: tags id; Type: DEFAULT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.tags ALTER COLUMN id SET DEFAULT nextval('documents.tags_id_seq'::regclass);


--
-- Name: tags_id_seq; Type: SEQUENCE SET; Schema: documents; Owner: postgres
--

SELECT pg_catalog.setval('documents.tags_id_seq', 8, true);


--
-- Name: document_tag_link document_tag_link_pkey; Type: CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.document_tag_link
    ADD CONSTRAINT document_tag_link_pkey PRIMARY KEY (document_id, tag_id);


--
-- Name: documents documents_minio_object_key_key; Type: CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.documents
    ADD CONSTRAINT documents_minio_object_key_key UNIQUE (minio_object_key);


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);


--
-- Name: tags tags_name_key; Type: CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.tags
    ADD CONSTRAINT tags_name_key UNIQUE (name);


--
-- Name: tags tags_pkey; Type: CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (id);


--
-- Name: ix_documents_geo_object_id; Type: INDEX; Schema: documents; Owner: postgres
--

CREATE INDEX ix_documents_geo_object_id ON documents.documents USING btree (geo_object_id);


--
-- Name: document_tag_link document_tag_link_document_id_fkey; Type: FK CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.document_tag_link
    ADD CONSTRAINT document_tag_link_document_id_fkey FOREIGN KEY (document_id) REFERENCES documents.documents(id) ON DELETE CASCADE;


--
-- Name: document_tag_link document_tag_link_tag_id_fkey; Type: FK CONSTRAINT; Schema: documents; Owner: postgres
--

ALTER TABLE ONLY documents.document_tag_link
    ADD CONSTRAINT document_tag_link_tag_id_fkey FOREIGN KEY (tag_id) REFERENCES documents.tags(id) ON DELETE CASCADE;

