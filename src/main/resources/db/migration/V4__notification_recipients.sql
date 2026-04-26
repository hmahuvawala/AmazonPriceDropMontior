CREATE TABLE notification_recipients (
    id              SMALLINT PRIMARY KEY,
    email_to_csv    VARCHAR(4000) NOT NULL DEFAULT '',
    sms_to_csv      VARCHAR(500)  NOT NULL DEFAULT ''
);

INSERT INTO notification_recipients (id, email_to_csv, sms_to_csv)
VALUES (1, '', '');
