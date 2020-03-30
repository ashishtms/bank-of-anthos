CREATE TABLE TRANSACTIONS (
    TRANSACTION_ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    FROM_ACCT CHAR(10),
    TO_ACCT CHAR(10),
    FROM_ROUTE CHAR(9),
    TO_ROUTE CHAR(9),
    AMOUNT INT,
    TIMESTAMP TIMESTAMP
)
