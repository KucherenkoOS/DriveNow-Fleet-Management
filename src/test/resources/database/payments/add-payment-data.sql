INSERT INTO users (id, email, first_name, last_name, password, is_deleted)
VALUES (100, 'user@example.com', 'John', 'Doe', 'password', false);

INSERT INTO users_roles (user_id, role_id) VALUES (100, 1);

INSERT INTO cars (id, model, brand, car_type, inventory, daily_fee, is_deleted)
VALUES (100, 'Camry', 'Toyota', 'SEDAN', 5, 50.00, false);

INSERT INTO rentals (id, rental_date, return_date, actual_return_date, car_id, user_id, is_deleted)
VALUES (100, '2026-06-01', '2026-06-08', '2026-06-07', 100, 100, false);

INSERT INTO payments (id, status, type, rental_id, session_url, session_id, total, is_deleted)
VALUES (10, 'EXPIRED', 'PAYMENT', 100, 'https://checkout.stripe.com/test_session', 'cs_test_123', 350.00, false);