-- 更新 Sprint 数据，添加 committed_points, completed_points, velocity

-- Sprint 1 - 已完成，有完整数据
UPDATE sprints
SET total_capacity = 80.0,
    committed_points = 50.0,
    completed_points = 48.0,
    velocity = 3.43
WHERE id = 1;

-- Sprint 2 - 进行中，部分完成
UPDATE sprints
SET total_capacity = 80.0,
    committed_points = 55.0,
    completed_points = 25.0,
    velocity = NULL
WHERE id = 2;

-- Sprint 3 - 进行中，刚开始
UPDATE sprints
SET total_capacity = 70.0,
    committed_points = 40.0,
    completed_points = 15.0,
    velocity = NULL
WHERE id = 3;
