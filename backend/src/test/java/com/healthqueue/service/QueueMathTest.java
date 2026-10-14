package com.healthqueue.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueueMathTest {

    @Test
    void threePatientsAheadAtFiveMinutesIsFifteenMinutes() {
        assertThat(QueueMath.estimateWaitMinutes(3, 5)).isEqualTo(15);
    }

    @Test
    void nobodyAheadMeansNoWait() {
        assertThat(QueueMath.estimateWaitMinutes(0, 5)).isZero();
    }

    @Test
    void negativeOrZeroInputsNeverProduceNegativeWait() {
        assertThat(QueueMath.estimateWaitMinutes(-2, 5)).isZero();
        assertThat(QueueMath.estimateWaitMinutes(4, 0)).isZero();
    }
}
