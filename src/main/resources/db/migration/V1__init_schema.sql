CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(180) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE queues (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  prefix VARCHAR(8) NOT NULL,
  description VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  weight_normal INT NOT NULL DEFAULT 1,
  weight_preferential INT NOT NULL DEFAULT 2,
  weight_urgent INT NOT NULL DEFAULT 3,
  ticket_counter BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_queues_name UNIQUE (name),
  CONSTRAINT uk_queues_prefix UNIQUE (prefix)
);

CREATE TABLE cycles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  queue_id BIGINT NOT NULL,
  number INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  active_queue_id BIGINT NULL,
  started_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6) NULL,
  CONSTRAINT fk_cycles_queue FOREIGN KEY (queue_id) REFERENCES queues(id),
  CONSTRAINT uk_cycles_queue_number UNIQUE (queue_id, number),
  CONSTRAINT uk_cycles_active_queue UNIQUE (active_queue_id)
);

CREATE TABLE tickets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  public_id CHAR(36) NOT NULL,
  queue_id BIGINT NOT NULL,
  cycle_id BIGINT NOT NULL,
  code VARCHAR(20) NOT NULL,
  customer_name VARCHAR(120) NOT NULL,
  priority VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  calls_in_cycle INT NOT NULL DEFAULT 0,
  cycle_weight INT NOT NULL,
  external_reference VARCHAR(120) NULL,
  called_at DATETIME(6) NULL,
  finished_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_tickets_queue FOREIGN KEY (queue_id) REFERENCES queues(id),
  CONSTRAINT fk_tickets_cycle FOREIGN KEY (cycle_id) REFERENCES cycles(id),
  CONSTRAINT uk_tickets_public_id UNIQUE (public_id),
  CONSTRAINT uk_tickets_queue_code UNIQUE (queue_id, code),
  CONSTRAINT uk_tickets_queue_external_ref UNIQUE (queue_id, external_reference)
);

CREATE TABLE cycle_calls (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cycle_id BIGINT NOT NULL,
  ticket_id BIGINT NOT NULL,
  attendant_id BIGINT NOT NULL,
  sequence_number INT NOT NULL,
  called_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_calls_cycle FOREIGN KEY (cycle_id) REFERENCES cycles(id),
  CONSTRAINT fk_calls_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
  CONSTRAINT fk_calls_attendant FOREIGN KEY (attendant_id) REFERENCES users(id),
  CONSTRAINT uk_calls_cycle_sequence UNIQUE (cycle_id, sequence_number)
);
