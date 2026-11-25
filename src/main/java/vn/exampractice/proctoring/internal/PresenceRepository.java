package vn.exampractice.proctoring.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Tín hiệu gần nhất của từng bài làm; khóa là ID bài làm. */
interface PresenceRepository extends JpaRepository<Presence, Long> {
}
