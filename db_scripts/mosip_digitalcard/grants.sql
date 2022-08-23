\c mosip_master 

GRANT CONNECT
   ON DATABASE mosip_digitalcard
   TO digitalcarduser;

GRANT USAGE
   ON SCHEMA digitalcard
   TO digitalcarduser;

GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES
   ON ALL TABLES IN SCHEMA digitalcard
   TO digitalcarduser;

ALTER DEFAULT PRIVILEGES IN SCHEMA digitalcard 
	GRANT SELECT,INSERT,UPDATE,DELETE,REFERENCES ON TABLES TO digitalcard;

