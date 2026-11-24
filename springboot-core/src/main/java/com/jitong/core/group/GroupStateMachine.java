package com.jitong.core.group;

/**
 * 合报状态机：
 *
 * OPEN --JOIN--> OPEN
 * OPEN --BEGIN_SETTLE--> SETTLING --SETTLED--> CLOSED
 * OPEN --CANCEL--> CANCELLED
 * SETTLING --REOPEN--> OPEN（结算失败时回滚/重开）
 *
 * CLOSED / CANCELLED 为终态。
 * 结算在同一数据库事务内完成：SETTLING 只在事务内可见，
 * 任何异常都会回滚到 OPEN，不会留下“卡在结算中”的脏状态。
 */
public final class GroupStateMachine {

    private GroupStateMachine() {
    }

    public static boolean canTransition(GroupStatus from, GroupAction action) {
        if (from == null || action == null) {
            return false;
        }
        return switch (from) {
            case OPEN -> action == GroupAction.JOIN
                    || action == GroupAction.BEGIN_SETTLE
                    || action == GroupAction.CANCEL;
            case SETTLING -> action == GroupAction.SETTLED
                    || action == GroupAction.REOPEN;
            case CLOSED, CANCELLED -> false;
        };
    }

    public static GroupStatus next(GroupStatus from, GroupAction action) {
        if (!canTransition(from, action)) {
            throw new IllegalStateException("非法合报状态迁移：" + from + " --" + action + "-->");
        }
        return switch (action) {
            case JOIN -> GroupStatus.OPEN;
            case BEGIN_SETTLE -> GroupStatus.SETTLING;
            case SETTLED -> GroupStatus.CLOSED;
            case CANCEL -> GroupStatus.CANCELLED;
            case REOPEN -> GroupStatus.OPEN;
        };
    }
}
