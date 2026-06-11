SET search_path TO scf;

-- EA-036 quasi-staging demo passwords only. Replace/rotate before any shared UAT.
UPDATE sys_user
SET password_hash = CASE login_name
    WHEN 'platform_admin' THEN '$2a$10$HX//zw/o4xGXiQthcndLb.QXUIrwGgLfE2vpDeJVoQRL/0Q2oGwZ6'
    WHEN 'funding_user' THEN '$2a$10$KGqlbMFV0UlJegiYbyMuc.hTefmJBEy4s29fJJsPwF21mF7OeS2Q6'
    WHEN 'member_user' THEN '$2a$10$AYp0x148tEdyf8R7FXsAfOU3.enGgGaiAqJuGvuMUsnIWHRI5qG4e'
    WHEN 'warehouse_user' THEN '$2a$10$FpurCMS6jF/3jQumdOx03eRFJngJyHNGYSOOTdcyg1ha54MqmNJkK'
    ELSE password_hash
END
WHERE login_name IN ('platform_admin', 'funding_user', 'member_user', 'warehouse_user');
