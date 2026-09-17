package io.github.somehow.mysite.journal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.journal.dao.entity.SjGoalCheckinDO;
import io.github.somehow.mysite.journal.dao.entity.SjGoalDO;
import io.github.somehow.mysite.journal.dao.entity.SjGoalTaskDO;
import io.github.somehow.mysite.journal.dao.mapper.SjGoalCheckinMapper;
import io.github.somehow.mysite.journal.dao.mapper.SjGoalMapper;
import io.github.somehow.mysite.journal.dao.mapper.SjGoalTaskMapper;
import io.github.somehow.mysite.journal.dto.CompleteTaskReqDTO;
import io.github.somehow.mysite.journal.dto.DayMarkDTO;
import io.github.somehow.mysite.journal.dto.GoalCheckInDTO;
import io.github.somehow.mysite.journal.dto.GoalCheckInReqDTO;
import io.github.somehow.mysite.journal.dto.GoalCreateReqDTO;
import io.github.somehow.mysite.journal.dto.GoalDetailDTO;
import io.github.somehow.mysite.journal.dto.GoalMonthDTO;
import io.github.somehow.mysite.journal.dto.GoalSummaryDTO;
import io.github.somehow.mysite.journal.dto.GoalTaskDTO;
import io.github.somehow.mysite.journal.dto.GoalTaskReqDTO;
import io.github.somehow.mysite.journal.dto.GoalUpdateReqDTO;
import io.github.somehow.mysite.journal.dto.LearningItemDTO;
import io.github.somehow.mysite.journal.dto.TodayGoalItemDTO;
import io.github.somehow.mysite.journal.dto.TodayGoalsDTO;
import io.github.somehow.mysite.journal.dto.WeekProgressDTO;
import io.github.somehow.mysite.journal.service.JournalGoalService;
import io.github.somehow.mysite.journal.service.JournalService;
import io.github.somehow.mysite.journal.util.GoalProgress;
import io.github.somehow.mysite.journal.util.JournalDates;
import io.github.somehow.mysite.journal.util.JournalIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalGoalServiceImpl implements JournalGoalService {

    public static final String TYPE_COUNT = "COUNT";
    public static final String TYPE_CHECKLIST = "CHECKLIST";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private final SjGoalMapper goalMapper;
    private final SjGoalTaskMapper taskMapper;
    private final SjGoalCheckinMapper checkinMapper;
    private final JournalService journalService;

    @Override
    public GoalMonthDTO listByPeriod(Long userId, String period) {
        YearMonth ym = JournalDates.requirePeriod(period);
        List<SjGoalDO> goals = goalMapper.selectList(new LambdaQueryWrapper<SjGoalDO>()
                .eq(SjGoalDO::getUserId, userId)
                .eq(SjGoalDO::getPeriod, period)
                .ne(SjGoalDO::getStatus, STATUS_ARCHIVED)
                .orderByAsc(SjGoalDO::getSortOrder)
                .orderByAsc(SjGoalDO::getCreatedAt));
        LocalDate asOf = GoalProgress.asOfForPeriod(ym, LocalDate.now());
        Map<String, List<SjGoalTaskDO>> tasksByGoal = loadTasks(goalIds(goals));
        Map<String, List<SjGoalCheckinDO>> checkinsByGoal = loadCheckins(goalIds(goals));

        List<GoalSummaryDTO> summaries = new ArrayList<>();
        Map<String, DayMarkDTO> dayMarks = new HashMap<>();
        int percentSum = 0;
        int reachedCount = 0;
        for (SjGoalDO goal : goals) {
            List<SjGoalTaskDO> tasks = tasksByGoal.getOrDefault(goal.getId(), List.of());
            List<SjGoalCheckinDO> checkins = checkinsByGoal.getOrDefault(goal.getId(), List.of());
            GoalSummaryDTO summary = toSummary(goal, tasks, checkins, asOf, ym);
            summaries.add(summary);
            percentSum += summary.getPercent();
            if (Boolean.TRUE.equals(summary.getReached())) {
                reachedCount++;
            }
            accumulateMarks(dayMarks, goal, tasks, checkins);
        }

        GoalMonthDTO dto = new GoalMonthDTO();
        dto.setPeriod(period);
        dto.setGoalCount(summaries.size());
        dto.setReachedCount(reachedCount);
        dto.setMonthPercent(summaries.isEmpty() ? 0 : (int) Math.round(percentSum / (double) summaries.size()));
        dto.setGoals(summaries);
        dto.setDayMarks(dayMarks);
        return dto;
    }

    @Override
    public GoalDetailDTO getGoal(Long userId, String goalId) {
        return toDetail(requireGoal(userId, goalId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO createGoal(Long userId, GoalCreateReqDTO request) {
        YearMonth.parse(request.getPeriod());
        if (goalMapper.selectById(request.getId()) != null) {
            throw new ClientException(ErrorCode.JOURNAL_GOAL_DUPLICATE);
        }
        validateTypeFields(request.getType(), request.getTargetValue());
        long now = System.currentTimeMillis();
        SjGoalDO entity = new SjGoalDO();
        entity.setId(request.getId());
        entity.setUserId(userId);
        entity.setPeriod(request.getPeriod());
        entity.setTitle(request.getTitle().trim());
        entity.setType(request.getType());
        if (TYPE_COUNT.equals(request.getType())) {
            entity.setTargetValue(request.getTargetValue());
            entity.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : "");
        } else {
            entity.setTargetValue(null);
            entity.setUnit(null);
        }
        entity.setColor(request.getColor());
        entity.setNote(request.getNote() == null ? "" : request.getNote());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(userId, request.getPeriod()));
        entity.setStatus(STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        goalMapper.insert(entity);
        return toDetail(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO updateGoal(Long userId, String goalId, GoalUpdateReqDTO request) {
        SjGoalDO entity = requireGoal(userId, goalId);
        if (StringUtils.hasText(request.getTitle())) {
            entity.setTitle(request.getTitle().trim());
        }
        if (request.getColor() != null) {
            entity.setColor(request.getColor());
        }
        if (request.getNote() != null) {
            entity.setNote(request.getNote());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (TYPE_COUNT.equals(entity.getType())) {
            if (request.getTargetValue() != null) {
                if (request.getTargetValue() < 1) {
                    throw new ClientException(ErrorCode.JOURNAL_GOAL_TARGET_INVALID);
                }
                entity.setTargetValue(request.getTargetValue());
            }
            if (request.getUnit() != null) {
                entity.setUnit(request.getUnit());
            }
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        goalMapper.updateById(entity);
        return toDetail(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGoal(Long userId, String goalId) {
        SjGoalDO entity = findGoal(userId, goalId);
        if (entity == null) {
            return;
        }
        goalMapper.deleteById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO addTask(Long userId, String goalId, GoalTaskReqDTO request) {
        SjGoalDO goal = requireGoal(userId, goalId);
        requireType(goal, TYPE_CHECKLIST);
        if (!StringUtils.hasText(request.getId())) {
            throw new ClientException("子任务 id 不能为空", ErrorCode.PARAM_VALIDATION_ERROR);
        }
        if (taskMapper.selectById(request.getId()) != null) {
            throw new ClientException(ErrorCode.JOURNAL_GOAL_DUPLICATE);
        }
        long now = System.currentTimeMillis();
        SjGoalTaskDO task = new SjGoalTaskDO();
        task.setId(request.getId());
        task.setGoalId(goalId);
        applyTaskFields(task, request);
        task.setDone(false);
        task.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextTaskSort(goalId));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        touchGoal(goal);
        return toDetail(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO updateTask(Long userId, String goalId, String taskId, GoalTaskReqDTO request) {
        SjGoalDO goal = requireGoal(userId, goalId);
        requireType(goal, TYPE_CHECKLIST);
        SjGoalTaskDO task = requireTask(goalId, taskId);
        applyTaskFields(task, request);
        if (request.getSortOrder() != null) {
            task.setSortOrder(request.getSortOrder());
        }
        task.setUpdatedAt(System.currentTimeMillis());
        taskMapper.updateById(task);
        touchGoal(goal);
        return toDetail(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO deleteTask(Long userId, String goalId, String taskId) {
        SjGoalDO goal = requireGoal(userId, goalId);
        requireType(goal, TYPE_CHECKLIST);
        SjGoalTaskDO task = findTask(goalId, taskId);
        if (task != null) {
            removeSyncedLearning(userId, task.getLearningClientId(), resolveTaskSyncDate(task, null));
            taskMapper.deleteById(taskId);
            touchGoal(goal);
        }
        return toDetail(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO completeTask(Long userId, String goalId, String taskId, CompleteTaskReqDTO request) {
        SjGoalDO goal = requireGoal(userId, goalId);
        requireType(goal, TYPE_CHECKLIST);
        SjGoalTaskDO task = requireTask(goalId, taskId);
        boolean currentlyDone = Boolean.TRUE.equals(task.getDone());
        boolean nextDone = request.getDone() != null ? request.getDone() : !currentlyDone;
        long now = System.currentTimeMillis();
        if (nextDone) {
            task.setDone(true);
            task.setDoneAt(now);
            if (request.getDurationMin() != null) {
                task.setDurationMin(request.getDurationMin());
            }
            if (request.getReflection() != null) {
                task.setReflection(request.getReflection());
            }
            String syncDate = resolveTaskSyncDate(task, request.getDate());
            if (Boolean.TRUE.equals(request.getSyncLearning()) && task.getDurationMin() != null) {
                syncLearning(userId, syncDate, task.getId(), goal, task.getDurationMin(),
                        task.getReflection() == null ? "" : task.getReflection());
                task.setLearningClientId(task.getId());
            }
        } else {
            String syncDate = resolveTaskSyncDate(task, request.getDate());
            removeSyncedLearning(userId, task.getLearningClientId(), syncDate);
            task.setDone(false);
            task.setDoneAt(null);
            task.setLearningClientId(null);
        }
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        touchGoal(goal);
        return toDetail(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO upsertCheckIn(Long userId, String goalId, String date, GoalCheckInReqDTO request) {
        JournalDates.requireDate(date);
        SjGoalDO goal = requireGoal(userId, goalId);
        requireType(goal, TYPE_COUNT);
        if (!JournalDates.dateInPeriod(date, goal.getPeriod())) {
            throw new ClientException("打卡日期须在目标所属月份内", ErrorCode.JOURNAL_DATE_INVALID);
        }
        SjGoalCheckinDO existing = findCheckin(goalId, date);
        long now = System.currentTimeMillis();
        boolean creating = existing == null;
        if (creating) {
            existing = new SjGoalCheckinDO();
            existing.setId(StringUtils.hasText(request.getId()) ? request.getId() : JournalIds.nanoid());
            existing.setGoalId(goalId);
            existing.setDate(date);
            existing.setCreatedAt(now);
        }
        existing.setQuantity(request.getQuantity());
        existing.setDurationMin(request.getDurationMin());
        existing.setNote(request.getNote() == null ? "" : request.getNote());
        existing.setUpdatedAt(now);
        if (Boolean.TRUE.equals(request.getSyncLearning()) && existing.getDurationMin() != null) {
            syncLearning(userId, date, existing.getId(), goal, existing.getDurationMin(), existing.getNote());
            existing.setLearningClientId(existing.getId());
        }
        if (creating) {
            checkinMapper.insert(existing);
        } else {
            checkinMapper.updateById(existing);
        }
        touchGoal(goal);
        return toDetail(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO deleteCheckIn(Long userId, String goalId, String date) {
        JournalDates.requireDate(date);
        SjGoalDO goal = requireGoal(userId, goalId);
        requireType(goal, TYPE_COUNT);
        SjGoalCheckinDO existing = findCheckin(goalId, date);
        if (existing != null) {
            removeSyncedLearning(userId, existing.getLearningClientId(), date);
            checkinMapper.deleteById(existing.getId());
            touchGoal(goal);
        }
        return toDetail(goal);
    }

    @Override
    public TodayGoalsDTO listToday(Long userId, String date) {
        String day = StringUtils.hasText(date) ? date : LocalDate.now().toString();
        JournalDates.requireDate(day);
        String period = day.substring(0, 7);
        YearMonth ym = YearMonth.parse(period);
        LocalDate asOf = LocalDate.parse(day);
        String thisMonday = JournalDates.mondayOf(asOf);

        List<SjGoalDO> goals = goalMapper.selectList(new LambdaQueryWrapper<SjGoalDO>()
                .eq(SjGoalDO::getUserId, userId)
                .eq(SjGoalDO::getPeriod, period)
                .ne(SjGoalDO::getStatus, STATUS_ARCHIVED)
                .orderByAsc(SjGoalDO::getSortOrder));
        Map<String, List<SjGoalTaskDO>> tasksByGoal = loadTasks(goalIds(goals));
        Map<String, List<SjGoalCheckinDO>> checkinsByGoal = loadCheckins(goalIds(goals));

        List<TodayGoalItemDTO> items = new ArrayList<>();
        for (SjGoalDO goal : goals) {
            List<SjGoalTaskDO> tasks = tasksByGoal.getOrDefault(goal.getId(), List.of());
            List<SjGoalCheckinDO> checkins = checkinsByGoal.getOrDefault(goal.getId(), List.of());
            GoalSummaryDTO summary = toSummary(goal, tasks, checkins, asOf, ym);
            TodayGoalItemDTO item = new TodayGoalItemDTO();
            item.setGoalId(goal.getId());
            item.setTitle(goal.getTitle());
            item.setType(goal.getType());
            item.setColor(goal.getColor());
            item.setPercent(summary.getPercent());
            item.setReached(summary.getReached());
            item.setUnit(goal.getUnit());
            item.setTargetValue(goal.getTargetValue());
            item.setActualValue(summary.getActualValue());
            item.setRemaining(summary.getRemaining());

            if (TYPE_COUNT.equals(goal.getType())) {
                item.setSuggestedToday(summary.getSuggestedDaily());
                SjGoalCheckinDO today = checkins.stream()
                        .filter(c -> day.equals(c.getDate()))
                        .findFirst()
                        .orElse(null);
                item.setTodayCheckin(today == null ? null : toCheckinDTO(today));
                item.setTodayTasks(List.of());
                if (today != null || summary.getSuggestedDaily() > 0) {
                    items.add(item);
                }
            } else {
                List<GoalTaskDTO> todayTasks = tasks.stream()
                        .filter(t -> isTodayTask(t, day, thisMonday))
                        .map(this::toTaskDTO)
                        .toList();
                item.setTodayTasks(todayTasks);
                item.setSuggestedToday(null);
                if (!todayTasks.isEmpty()) {
                    items.add(item);
                }
            }
        }
        TodayGoalsDTO dto = new TodayGoalsDTO();
        dto.setDate(day);
        dto.setItems(items);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoalDetailDTO carryOver(Long userId, String goalId) {
        SjGoalDO source = requireGoal(userId, goalId);
        String nextPeriod = JournalDates.nextPeriod(source.getPeriod());
        long now = System.currentTimeMillis();
        SjGoalDO copy = new SjGoalDO();
        copy.setId(JournalIds.nanoid());
        copy.setUserId(userId);
        copy.setPeriod(nextPeriod);
        copy.setTitle(source.getTitle());
        copy.setType(source.getType());
        copy.setTargetValue(source.getTargetValue());
        copy.setUnit(source.getUnit());
        copy.setColor(source.getColor());
        copy.setNote(source.getNote());
        copy.setSortOrder(nextSortOrder(userId, nextPeriod));
        copy.setStatus(STATUS_ACTIVE);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        goalMapper.insert(copy);

        if (TYPE_CHECKLIST.equals(source.getType())) {
            List<SjGoalTaskDO> tasks = listTasks(source.getId());
            int order = 0;
            for (SjGoalTaskDO task : tasks) {
                if (Boolean.TRUE.equals(task.getDone())) {
                    continue;
                }
                SjGoalTaskDO next = new SjGoalTaskDO();
                next.setId(JournalIds.nanoid());
                next.setGoalId(copy.getId());
                next.setTitle(task.getTitle());
                next.setDueDate(shiftDateToPeriod(task.getDueDate(), source.getPeriod(), nextPeriod));
                next.setWeekStart(shiftDateToPeriod(task.getWeekStart(), source.getPeriod(), nextPeriod));
                next.setDone(false);
                next.setSortOrder(order++);
                next.setCreatedAt(now);
                next.setUpdatedAt(now);
                taskMapper.insert(next);
            }
        }
        return toDetail(copy);
    }

    // ─── 内部 ────────────────────────────────────

    private void validateTypeFields(String type, Integer targetValue) {
        if (TYPE_COUNT.equals(type)) {
            if (targetValue == null || targetValue < 1) {
                throw new ClientException(ErrorCode.JOURNAL_GOAL_TARGET_INVALID);
            }
        } else if (!TYPE_CHECKLIST.equals(type)) {
            throw new ClientException(ErrorCode.JOURNAL_GOAL_TYPE_MISMATCH);
        }
    }

    private void requireType(SjGoalDO goal, String expected) {
        if (!expected.equals(goal.getType())) {
            throw new ClientException(ErrorCode.JOURNAL_GOAL_TYPE_MISMATCH);
        }
    }

    private SjGoalDO requireGoal(Long userId, String goalId) {
        SjGoalDO goal = findGoal(userId, goalId);
        if (goal == null) {
            throw new ClientException(ErrorCode.JOURNAL_GOAL_NOT_FOUND);
        }
        return goal;
    }

    private SjGoalDO findGoal(Long userId, String goalId) {
        return goalMapper.selectOne(new LambdaQueryWrapper<SjGoalDO>()
                .eq(SjGoalDO::getId, goalId)
                .eq(SjGoalDO::getUserId, userId));
    }

    private SjGoalTaskDO requireTask(String goalId, String taskId) {
        SjGoalTaskDO task = findTask(goalId, taskId);
        if (task == null) {
            throw new ClientException(ErrorCode.JOURNAL_TASK_NOT_FOUND);
        }
        return task;
    }

    private SjGoalTaskDO findTask(String goalId, String taskId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<SjGoalTaskDO>()
                .eq(SjGoalTaskDO::getId, taskId)
                .eq(SjGoalTaskDO::getGoalId, goalId));
    }

    private SjGoalCheckinDO findCheckin(String goalId, String date) {
        return checkinMapper.selectOne(new LambdaQueryWrapper<SjGoalCheckinDO>()
                .eq(SjGoalCheckinDO::getGoalId, goalId)
                .eq(SjGoalCheckinDO::getDate, date));
    }

    private void applyTaskFields(SjGoalTaskDO task, GoalTaskReqDTO request) {
        task.setTitle(request.getTitle().trim());
        String due = JournalDates.blankToNull(request.getDueDate());
        if (due != null) {
            JournalDates.requireDate(due);
        }
        String weekStart = JournalDates.blankToNull(request.getWeekStart());
        if (weekStart != null) {
            JournalDates.requireDate(weekStart);
            weekStart = JournalDates.mondayOf(LocalDate.parse(weekStart));
        }
        task.setDueDate(due);
        task.setWeekStart(weekStart);
    }

    private int nextSortOrder(Long userId, String period) {
        List<SjGoalDO> existing = goalMapper.selectList(new LambdaQueryWrapper<SjGoalDO>()
                .eq(SjGoalDO::getUserId, userId)
                .eq(SjGoalDO::getPeriod, period)
                .orderByDesc(SjGoalDO::getSortOrder)
                .last("LIMIT 1"));
        if (existing.isEmpty() || existing.get(0).getSortOrder() == null) {
            return 0;
        }
        return existing.get(0).getSortOrder() + 1;
    }

    private int nextTaskSort(String goalId) {
        List<SjGoalTaskDO> existing = taskMapper.selectList(new LambdaQueryWrapper<SjGoalTaskDO>()
                .eq(SjGoalTaskDO::getGoalId, goalId)
                .orderByDesc(SjGoalTaskDO::getSortOrder)
                .last("LIMIT 1"));
        if (existing.isEmpty() || existing.get(0).getSortOrder() == null) {
            return 0;
        }
        return existing.get(0).getSortOrder() + 1;
    }

    private void touchGoal(SjGoalDO goal) {
        goal.setUpdatedAt(System.currentTimeMillis());
        goalMapper.updateById(goal);
    }

    private List<String> goalIds(List<SjGoalDO> goals) {
        return goals.stream().map(SjGoalDO::getId).toList();
    }

    private Map<String, List<SjGoalTaskDO>> loadTasks(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SjGoalTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<SjGoalTaskDO>()
                .in(SjGoalTaskDO::getGoalId, ids)
                .orderByAsc(SjGoalTaskDO::getSortOrder)
                .orderByAsc(SjGoalTaskDO::getCreatedAt));
        return tasks.stream().collect(Collectors.groupingBy(SjGoalTaskDO::getGoalId));
    }

    private Map<String, List<SjGoalCheckinDO>> loadCheckins(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SjGoalCheckinDO> checkins = checkinMapper.selectList(new LambdaQueryWrapper<SjGoalCheckinDO>()
                .in(SjGoalCheckinDO::getGoalId, ids)
                .orderByAsc(SjGoalCheckinDO::getDate));
        return checkins.stream().collect(Collectors.groupingBy(SjGoalCheckinDO::getGoalId));
    }

    private List<SjGoalTaskDO> listTasks(String goalId) {
        return taskMapper.selectList(new LambdaQueryWrapper<SjGoalTaskDO>()
                .eq(SjGoalTaskDO::getGoalId, goalId)
                .orderByAsc(SjGoalTaskDO::getSortOrder)
                .orderByAsc(SjGoalTaskDO::getCreatedAt));
    }

    private List<SjGoalCheckinDO> listCheckins(String goalId) {
        return checkinMapper.selectList(new LambdaQueryWrapper<SjGoalCheckinDO>()
                .eq(SjGoalCheckinDO::getGoalId, goalId)
                .orderByAsc(SjGoalCheckinDO::getDate));
    }

    private GoalDetailDTO toDetail(SjGoalDO goal) {
        YearMonth ym = YearMonth.parse(goal.getPeriod());
        LocalDate asOf = GoalProgress.asOfForPeriod(ym, LocalDate.now());
        List<SjGoalTaskDO> tasks = listTasks(goal.getId());
        List<SjGoalCheckinDO> checkins = listCheckins(goal.getId());
        GoalDetailDTO dto = new GoalDetailDTO();
        fillSummary(dto, goal, tasks, checkins, asOf, ym);
        dto.setTasks(tasks.stream().map(this::toTaskDTO).toList());
        dto.setCheckins(checkins.stream().map(this::toCheckinDTO).toList());
        dto.setWeeks(buildWeeks(goal, checkins, ym));
        return dto;
    }

    private GoalSummaryDTO toSummary(
            SjGoalDO goal,
            List<SjGoalTaskDO> tasks,
            List<SjGoalCheckinDO> checkins,
            LocalDate asOf,
            YearMonth ym) {
        GoalSummaryDTO dto = new GoalSummaryDTO();
        fillSummary(dto, goal, tasks, checkins, asOf, ym);
        return dto;
    }

    private void fillSummary(
            GoalSummaryDTO dto,
            SjGoalDO goal,
            List<SjGoalTaskDO> tasks,
            List<SjGoalCheckinDO> checkins,
            LocalDate asOf,
            YearMonth ym) {
        dto.setId(goal.getId());
        dto.setPeriod(goal.getPeriod());
        dto.setTitle(goal.getTitle());
        dto.setType(goal.getType());
        dto.setTargetValue(goal.getTargetValue());
        dto.setUnit(goal.getUnit());
        dto.setColor(goal.getColor());
        dto.setNote(goal.getNote());
        dto.setSortOrder(goal.getSortOrder());
        dto.setStatus(goal.getStatus());
        dto.setCreatedAt(goal.getCreatedAt());
        dto.setUpdatedAt(goal.getUpdatedAt());

        if (TYPE_COUNT.equals(goal.getType())) {
            int target = goal.getTargetValue() == null ? 0 : goal.getTargetValue();
            int actual = checkins.stream().mapToInt(c -> c.getQuantity() == null ? 0 : c.getQuantity()).sum();
            int remaining = Math.max(0, target - actual);
            int daysInMonth = ym.lengthOfMonth();
            int weeksInMonth = Math.max(1, GoalProgress.weeksIntersecting(ym).size());
            dto.setActualValue(actual);
            dto.setTotalValue(target);
            dto.setRemaining(remaining);
            dto.setPercent(GoalProgress.percent(actual, target));
            dto.setReached(target > 0 && actual >= target);
            dto.setPlannedDaily(GoalProgress.suggestedQuota(target, daysInMonth));
            dto.setPlannedWeekly(GoalProgress.suggestedQuota(target, weeksInMonth));
            dto.setRemainingDays(GoalProgress.remainingDays(asOf, ym));
            dto.setSuggestedDaily(GoalProgress.suggestedQuota(remaining, dto.getRemainingDays()));
            dto.setSuggestedWeekly(GoalProgress.suggestedQuota(remaining, GoalProgress.remainingWeeks(asOf, ym)));
        } else {
            int total = tasks.size();
            int done = (int) tasks.stream().filter(t -> Boolean.TRUE.equals(t.getDone())).count();
            dto.setActualValue(done);
            dto.setTotalValue(total);
            dto.setRemaining(Math.max(0, total - done));
            dto.setPercent(GoalProgress.percent(done, total));
            dto.setReached(total > 0 && done == total);
            dto.setPlannedDaily(null);
            dto.setPlannedWeekly(null);
            dto.setSuggestedDaily(null);
            dto.setSuggestedWeekly(null);
            dto.setRemainingDays(null);
        }
    }

    private List<WeekProgressDTO> buildWeeks(SjGoalDO goal, List<SjGoalCheckinDO> checkins, YearMonth ym) {
        if (!TYPE_COUNT.equals(goal.getType())) {
            return List.of();
        }
        int planned = GoalProgress.suggestedQuota(
                goal.getTargetValue() == null ? 0 : goal.getTargetValue(),
                Math.max(1, GoalProgress.weeksIntersecting(ym).size()));
        List<WeekProgressDTO> weeks = new ArrayList<>();
        for (LocalDate[] range : GoalProgress.weeksIntersecting(ym)) {
            String start = range[0].toString();
            String end = range[1].toString();
            int actual = checkins.stream()
                    .filter(c -> c.getDate().compareTo(start) >= 0 && c.getDate().compareTo(end) <= 0)
                    .mapToInt(c -> c.getQuantity() == null ? 0 : c.getQuantity())
                    .sum();
            WeekProgressDTO week = new WeekProgressDTO();
            week.setWeekStart(start);
            week.setWeekEnd(end);
            week.setActual(actual);
            week.setPlanned(planned);
            weeks.add(week);
        }
        return weeks;
    }

    private void accumulateMarks(
            Map<String, DayMarkDTO> dayMarks,
            SjGoalDO goal,
            List<SjGoalTaskDO> tasks,
            List<SjGoalCheckinDO> checkins) {
        if (TYPE_COUNT.equals(goal.getType())) {
            for (SjGoalCheckinDO checkin : checkins) {
                bumpMark(dayMarks, checkin.getDate(), true);
            }
        } else {
            for (SjGoalTaskDO task : tasks) {
                if (StringUtils.hasText(task.getDueDate())) {
                    bumpMark(dayMarks, task.getDueDate(), Boolean.TRUE.equals(task.getDone()));
                } else if (StringUtils.hasText(task.getWeekStart())) {
                    bumpMark(dayMarks, task.getWeekStart(), Boolean.TRUE.equals(task.getDone()));
                }
            }
        }
    }

    private void bumpMark(Map<String, DayMarkDTO> dayMarks, String date, boolean done) {
        DayMarkDTO mark = dayMarks.computeIfAbsent(date, key -> {
            DayMarkDTO created = new DayMarkDTO();
            created.setTotal(0);
            created.setDone(0);
            return created;
        });
        mark.setTotal(mark.getTotal() + 1);
        if (done) {
            mark.setDone(mark.getDone() + 1);
        }
    }

    private boolean isTodayTask(SjGoalTaskDO task, String date, String thisMonday) {
        if (Boolean.TRUE.equals(task.getDone())) {
            if (date.equals(task.getDueDate()) || thisMonday.equals(task.getWeekStart())) {
                return true;
            }
            return false;
        }
        if (date.equals(task.getDueDate())) {
            return true;
        }
        if (task.getDueDate() != null && task.getDueDate().compareTo(date) < 0) {
            return true;
        }
        return thisMonday.equals(task.getWeekStart());
    }

    private String resolveTaskSyncDate(SjGoalTaskDO task, String requested) {
        if (StringUtils.hasText(requested)) {
            JournalDates.requireDate(requested);
            return requested;
        }
        if (StringUtils.hasText(task.getDueDate())) {
            return task.getDueDate();
        }
        return LocalDate.now().toString();
    }

    private String shiftDateToPeriod(String date, String fromPeriod, String toPeriod) {
        if (!StringUtils.hasText(date) || !date.startsWith(fromPeriod)) {
            return date;
        }
        YearMonth to = YearMonth.parse(toPeriod);
        int day = Integer.parseInt(date.substring(8));
        int clamped = Math.min(day, to.lengthOfMonth());
        return to.atDay(clamped).toString();
    }

    private void syncLearning(
            Long userId,
            String date,
            String clientId,
            SjGoalDO goal,
            int durationMin,
            String note) {
        LearningItemDTO item = new LearningItemDTO();
        item.setId(clientId);
        item.setSubject(goal.getTitle());
        item.setDurationMin(durationMin);
        item.setNote(note == null ? "" : note);
        item.setColor(goal.getColor());
        journalService.upsertLearningItem(userId, date, item);
    }

    private void removeSyncedLearning(Long userId, String clientId, String date) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(date)) {
            return;
        }
        journalService.removeLearningItem(userId, date, clientId);
    }

    private GoalTaskDTO toTaskDTO(SjGoalTaskDO entity) {
        GoalTaskDTO dto = new GoalTaskDTO();
        dto.setId(entity.getId());
        dto.setGoalId(entity.getGoalId());
        dto.setTitle(entity.getTitle());
        dto.setDueDate(entity.getDueDate());
        dto.setWeekStart(entity.getWeekStart());
        dto.setDone(Boolean.TRUE.equals(entity.getDone()));
        dto.setDoneAt(entity.getDoneAt());
        dto.setDurationMin(entity.getDurationMin());
        dto.setReflection(entity.getReflection());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private GoalCheckInDTO toCheckinDTO(SjGoalCheckinDO entity) {
        GoalCheckInDTO dto = new GoalCheckInDTO();
        dto.setId(entity.getId());
        dto.setGoalId(entity.getGoalId());
        dto.setDate(entity.getDate());
        dto.setQuantity(entity.getQuantity());
        dto.setDurationMin(entity.getDurationMin());
        dto.setNote(entity.getNote());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
