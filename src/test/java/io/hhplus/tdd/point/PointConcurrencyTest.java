package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PointConcurrencyTest {

  @Autowired
  PointService pointService;
  @Autowired
  UserPointTable userPointTable;
  @Autowired
  PointHistoryTable pointHistoryTable;

  @BeforeEach
  void setUp() {
    userPointTable.insertOrUpdate(1L, 0);
  }

  @Test
  @DisplayName("충전 동시 요청 시 1억까지는 정확히 누적되고, 초과분은 예외로 처리된다")
  void chargePoint_UpToMaxPoint_UnderConcurrency() throws InterruptedException {
    // given
    long userId = 1L;
    int threadCount = 200;
    long amountPerCharge = 1000_000L;
    long maxPoint = 100_000_000L;
    long expectedSuccesses = maxPoint / amountPerCharge;

    ExecutorService executor = Executors.newFixedThreadPool(32);
    CountDownLatch latch = new CountDownLatch(threadCount);
    final int[] successCount = {0};
    final int[] failureCount = {0};

    // when
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          pointService.charge(userId, amountPerCharge);
          synchronized (successCount) {
            successCount[0]++;
          }
        } catch (IllegalStateException e) {
          synchronized (failureCount) {
            failureCount[0]++;
          }
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();

    // then
    UserPoint result = pointService.getPoint(userId);
    assertThat(result.point()).isEqualTo(maxPoint);
    assertThat(successCount[0]).isEqualTo(expectedSuccesses);
    assertThat(failureCount[0]).isEqualTo(threadCount - expectedSuccesses);
  }

  @Test
  @DisplayName("차감 동시 요청 시 잔고까지는 정확히 차감되고, 초과 요청은 예외로 처리된다")
  void usePoint_WhenPointIsInsufficient_UnderConcurrency() throws InterruptedException {
    // given
    long userId = 1L;
    int threadCount = 200;
    long amountPerUse = 1_000L;
    long initialPoint = 100_000L;
    long expectedSuccesses = initialPoint / amountPerUse;

    userPointTable.insertOrUpdate(userId, initialPoint);

    ExecutorService executor = Executors.newFixedThreadPool(16);
    CountDownLatch latch = new CountDownLatch(threadCount);
    final int[] successCount = {0};
    final int[] failureCount = {0};

    // when
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          pointService.use(userId, amountPerUse);
          synchronized (successCount) {
            successCount[0]++;
          }
        } catch (IllegalStateException e) {
          synchronized (failureCount) {
            failureCount[0]++;
          }
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();

    // then
    UserPoint result = pointService.getPoint(userId);
    assertThat(result.point()).isEqualTo(0L);
    assertThat(successCount[0]).isEqualTo(expectedSuccesses);
    assertThat(failureCount[0]).isEqualTo(threadCount - expectedSuccesses);
    assertThat(pointHistoryTable.selectAllByUserId(userId).stream()
        .filter(h -> h.type() == TransactionType.USE).count()).isEqualTo(expectedSuccesses);
  }

}
