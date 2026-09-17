package io.github.somehow.mysite.journal.service;

import io.github.somehow.mysite.journal.dto.CompleteTaskReqDTO;
import io.github.somehow.mysite.journal.dto.GoalCheckInReqDTO;
import io.github.somehow.mysite.journal.dto.GoalCreateReqDTO;
import io.github.somehow.mysite.journal.dto.GoalDetailDTO;
import io.github.somehow.mysite.journal.dto.GoalMonthDTO;
import io.github.somehow.mysite.journal.dto.GoalTaskReqDTO;
import io.github.somehow.mysite.journal.dto.GoalUpdateReqDTO;
import io.github.somehow.mysite.journal.dto.TodayGoalsDTO;

public interface JournalGoalService {

    GoalMonthDTO listByPeriod(Long userId, String period);

    GoalDetailDTO getGoal(Long userId, String goalId);

    GoalDetailDTO createGoal(Long userId, GoalCreateReqDTO request);

    GoalDetailDTO updateGoal(Long userId, String goalId, GoalUpdateReqDTO request);

    void deleteGoal(Long userId, String goalId);

    GoalDetailDTO addTask(Long userId, String goalId, GoalTaskReqDTO request);

    GoalDetailDTO updateTask(Long userId, String goalId, String taskId, GoalTaskReqDTO request);

    GoalDetailDTO deleteTask(Long userId, String goalId, String taskId);

    GoalDetailDTO completeTask(Long userId, String goalId, String taskId, CompleteTaskReqDTO request);

    GoalDetailDTO upsertCheckIn(Long userId, String goalId, String date, GoalCheckInReqDTO request);

    GoalDetailDTO deleteCheckIn(Long userId, String goalId, String date);

    TodayGoalsDTO listToday(Long userId, String date);

    GoalDetailDTO carryOver(Long userId, String goalId);
}
