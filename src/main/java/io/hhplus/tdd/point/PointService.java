package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class PointService {

  private static final long MAX_POINT = 100_000_000L;

  private final PointHistoryTable pointHistoryTable;
  private final UserPointTable userPointTable;

  private final ConcurrentHashMap<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

  public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
    this.userPointTable = userPointTable;
    this.pointHistoryTable = pointHistoryTable;
  }

  /**
   * 특정 유저의 포인트 조회
   */
  public UserPoint getPoint(long userId) {
    return userPointTable.selectById(userId);
  }

  /**
   * 특정 유저의 포인트 충전/이용 내역 조회
   */
  public List<PointHistory> getHistories(long userId) {
    return pointHistoryTable.selectAllByUserId(userId);
  }

  /**
   * 특정 유저의 포인트 충전
   */
  public UserPoint charge(long userId, long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
    }

    ReentrantLock lock = lockMap.computeIfAbsent(userId, id -> new ReentrantLock());
    lock.lock();
    try {
      UserPoint current = userPointTable.selectById(userId);
      long newAmount = current.point() + amount;
      if (newAmount > MAX_POINT) {
        throw new IllegalStateException("최대 포인트 한도(1억)를 초과할 수 없습니다.");
      }

      UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
      pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updated.updateMillis());
      return updated;
    } finally {
      lock.unlock();
    }
  }

  /**
   * 특정 유저의 포인트 사용
   */
  public UserPoint use(long userId, long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다.");
    }

    ReentrantLock lock = lockMap.computeIfAbsent(userId, id -> new ReentrantLock());
    lock.lock();
    try {
      UserPoint current = userPointTable.selectById(userId);
      if (current.point() < amount) {
        throw new IllegalStateException("포인트가 부족합니다.");
      }
      long newAmount = current.point() - amount;
      UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
      pointHistoryTable.insert(userId, amount, TransactionType.USE, updated.updateMillis());
      return updated;
    } finally {
      lock.unlock();
    }
  }
}
