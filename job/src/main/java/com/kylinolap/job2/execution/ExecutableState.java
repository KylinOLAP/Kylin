package com.kylinolap.job2.execution;

import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;

/**
 * Created by qianzhou on 12/15/14.
 */
public enum ExecutableState {

    READY,
    RUNNING,
    ERROR,
    STOPPED,
    DISCARDED,
    SUCCEED;

    private static Multimap<ExecutableState, ExecutableState> VALID_STATE_TRANSFER;

    static {
        VALID_STATE_TRANSFER = Multimaps.newSetMultimap(Maps.<ExecutableState, Collection<ExecutableState>>newEnumMap(ExecutableState.class), new Supplier<Set<ExecutableState>>() {
            @Override
            public Set<ExecutableState> get() {
                return Sets.newCopyOnWriteArraySet();
            }
        });

        //scheduler
        VALID_STATE_TRANSFER.put(ExecutableState.READY, ExecutableState.RUNNING);
        //user
        VALID_STATE_TRANSFER.put(ExecutableState.READY, ExecutableState.STOPPED);

        //job
        VALID_STATE_TRANSFER.put(ExecutableState.RUNNING, ExecutableState.READY);
        //job
        VALID_STATE_TRANSFER.put(ExecutableState.RUNNING, ExecutableState.SUCCEED);
        //user
        VALID_STATE_TRANSFER.put(ExecutableState.RUNNING, ExecutableState.STOPPED);
        //scheduler,job
        VALID_STATE_TRANSFER.put(ExecutableState.RUNNING, ExecutableState.ERROR);


        VALID_STATE_TRANSFER.put(ExecutableState.STOPPED, ExecutableState.DISCARDED);
        VALID_STATE_TRANSFER.put(ExecutableState.STOPPED, ExecutableState.READY);

        VALID_STATE_TRANSFER.put(ExecutableState.ERROR, ExecutableState.DISCARDED);
        VALID_STATE_TRANSFER.put(ExecutableState.ERROR, ExecutableState.READY);
    }

    public static boolean isValidStateTransfer(ExecutableState from, ExecutableState to) {
        return VALID_STATE_TRANSFER.containsEntry(from, to);
    }

}