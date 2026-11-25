package vn.exampractice.proctoring.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Nhật ký giám sát. */
interface ProctorEventRepository extends JpaRepository<ProctorEvent, Long> {
}
