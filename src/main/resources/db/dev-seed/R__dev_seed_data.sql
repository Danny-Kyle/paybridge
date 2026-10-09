-- CUSTOMERS (all KYC verified) ------------------------------------------------
INSERT INTO customer (id, first_name, last_name, email, phone_number, kyc_status, created_at, updated_at, version) VALUES
                                                                                                                       ('a1000000-0000-4000-8000-000000000001','Adaeze','Okafor','adaeze.okafor@example.com','08031230001','VERIFIED',now(),now(),0),
                                                                                                                       ('a1000000-0000-4000-8000-000000000002','Tunde','Bakare','tunde.bakare@example.com','08031230002','VERIFIED',now(),now(),0),
                                                                                                                       ('a1000000-0000-4000-8000-000000000003','Ngozi','Eze','ngozi.eze@example.com','08031230003','VERIFIED',now(),now(),0),
                                                                                                                       ('a1000000-0000-4000-8000-000000000004','Emeka','Nwosu','emeka.nwosu@example.com','08031230004','VERIFIED',now(),now(),0),
                                                                                                                       ('a1000000-0000-4000-8000-000000000005','Fatima','Bello','fatima.bello@example.com','08031230005','VERIFIED',now(),now(),0)
    ON CONFLICT DO NOTHING;

-- ACCOUNTS ---------------------------------------------------------------------
INSERT INTO account (id, customer_id, account_number, account_name, kind, bank_code, bank_name, currency, balance_minor, status, created_at, updated_at, version) VALUES
                                                                                                                                                                      ('b2000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000001','2000000001','Adaeze Okafor','INTERNAL','PBG','Paybridge','NGN',5000000000,'ACTIVE',now(),now(),0),
                                                                                                                                                                      ('b2000000-0000-4000-8000-000000000002','a1000000-0000-4000-8000-000000000002','2000000002','Tunde Bakare','INTERNAL','PBG','Paybridge','NGN',15000000,'ACTIVE',now(),now(),0),
                                                                                                                                                                      ('b2000000-0000-4000-8000-000000000003','a1000000-0000-4000-8000-000000000003','2000000003','Ngozi Eze','INTERNAL','PBG','Paybridge','NGN',250000000,'ACTIVE',now(),now(),0),
-- external: balance is 0 and NOT authoritative; we cannot see other banks' balances
                                                                                                                                                                      ('b2000000-0000-4000-8000-000000000004','a1000000-0000-4000-8000-000000000004','0000000000','Emeka Nwosu','EXTERNAL','057','Zenith Bank','NGN',0,'ACTIVE',now(),now(),0),
                                                                                                                                                                      ('b2000000-0000-4000-8000-000000000005','a1000000-0000-4000-8000-000000000005','0000000000','Fatima Bello','EXTERNAL','058','Guaranty Trust Bank','NGN',0,'ACTIVE',now(),now(),0)
    ON CONFLICT DO NOTHING;

-- NEXT OF KIN ------------------------------------------------------------------
INSERT INTO next_of_kin (id, customer_id, next_of_kin_customer_id, relationship, created_at, updated_at, version) VALUES
                                                                                                                      ('e5000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000004','SIBLING',now(),now(),0),
                                                                                                                      ('e5000000-0000-4000-8000-000000000002','a1000000-0000-4000-8000-000000000002','a1000000-0000-4000-8000-000000000003','SPOUSE',now(),now(),0),
                                                                                                                      ('e5000000-0000-4000-8000-000000000003','a1000000-0000-4000-8000-000000000003','a1000000-0000-4000-8000-000000000001','PARENT',now(),now(),0)
    ON CONFLICT DO NOTHING;

-- LEDGER OPENING BALANCES (so ledger and account balances reconcile from day one) ----------
INSERT INTO ledger_journal (id, reference, type, description, posted_at) VALUES
                                                                             ('c3000000-0000-4000-8000-000000000001','seed-opening-1','OPENING_BALANCE','Opening balance Adaeze',now()),
                                                                             ('c3000000-0000-4000-8000-000000000002','seed-opening-2','OPENING_BALANCE','Opening balance Tunde',now()),
                                                                             ('c3000000-0000-4000-8000-000000000003','seed-opening-3','OPENING_BALANCE','Opening balance Ngozi',now())
    ON CONFLICT DO NOTHING;

INSERT INTO ledger_entry (id, journal_id, account_ref, direction, amount_minor, currency, posted_at) VALUES
                                                                                                         ('d4000000-0000-4000-8000-000000000001','c3000000-0000-4000-8000-000000000001','SYSTEM:OPENING_EQUITY','DEBIT', 5000000000,'NGN',now()),
                                                                                                         ('d4000000-0000-4000-8000-000000000002','c3000000-0000-4000-8000-000000000001','CUSTOMER:b2000000-0000-4000-8000-000000000001','CREDIT',5000000000,'NGN',now()),
                                                                                                         ('d4000000-0000-4000-8000-000000000003','c3000000-0000-4000-8000-000000000002','SYSTEM:OPENING_EQUITY','DEBIT', 15000000,'NGN',now()),
                                                                                                         ('d4000000-0000-4000-8000-000000000004','c3000000-0000-4000-8000-000000000002','CUSTOMER:b2000000-0000-4000-8000-000000000002','CREDIT',15000000,'NGN',now()),
                                                                                                         ('d4000000-0000-4000-8000-000000000005','c3000000-0000-4000-8000-000000000003','SYSTEM:OPENING_EQUITY','DEBIT', 250000000,'NGN',now()),
                                                                                                         ('d4000000-0000-4000-8000-000000000006','c3000000-0000-4000-8000-000000000003','CUSTOMER:b2000000-0000-4000-8000-000000000003','CREDIT',250000000,'NGN',now())
    ON CONFLICT DO NOTHING;

-- SANCTIONS PLACEHOLDER (for the compliance demo) ----------------------------------
INSERT INTO sanctions_entry (id, name_normalized, source) VALUES
    ('f6000000-0000-4000-8000-000000000001','john doe blocked','DEV_PLACEHOLDER')
    ON CONFLICT DO NOTHING;