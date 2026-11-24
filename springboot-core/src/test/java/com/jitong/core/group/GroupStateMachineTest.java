package com.jitong.core.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GroupStateMachineTest {

    @Test
    void openCanJoinOrStartSettlementOrCancel() {
        assertThat(GroupStateMachine.next(GroupStatus.OPEN, GroupAction.JOIN)).isEqualTo(GroupStatus.OPEN);
        assertThat(GroupStateMachine.next(GroupStatus.OPEN, GroupAction.BEGIN_SETTLE))
                .isEqualTo(GroupStatus.SETTLING);
        assertThat(GroupStateMachine.next(GroupStatus.OPEN, GroupAction.CANCEL))
                .isEqualTo(GroupStatus.CANCELLED);
    }

    @Test
    void settlingCanCloseOrReopen() {
        assertThat(GroupStateMachine.next(GroupStatus.SETTLING, GroupAction.SETTLED))
                .isEqualTo(GroupStatus.CLOSED);
        assertThat(GroupStateMachine.next(GroupStatus.SETTLING, GroupAction.REOPEN))
                .isEqualTo(GroupStatus.OPEN);
    }

    @Test
    void terminalStatesRejectAllTransitions() {
        for (GroupStatus status : new GroupStatus[]{GroupStatus.CLOSED, GroupStatus.CANCELLED}) {
            for (GroupAction action : GroupAction.values()) {
                assertThat(GroupStateMachine.canTransition(status, action)).isFalse();
            }
        }
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> GroupStateMachine.next(GroupStatus.OPEN, GroupAction.SETTLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法合报状态迁移");
    }
}
