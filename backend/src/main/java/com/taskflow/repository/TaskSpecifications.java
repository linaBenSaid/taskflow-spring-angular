package com.taskflow.repository;

import com.taskflow.dto.TaskFilter;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Each method is one WHERE condition. forFilter() ANDs together only the filters that were given,
// so one query handles every combination (?status=, ?status=&priority=, nothing at all, ...).
public final class TaskSpecifications {

	private TaskSpecifications() {
	}

	public static Specification<Task> forFilter(Long projectId, TaskFilter filter) {
		List<Specification<Task>> conditions = new ArrayList<>();
		conditions.add(inProject(projectId));
		if (filter.status() != null) {
			conditions.add(hasStatus(filter.status()));
		}
		if (filter.priority() != null) {
			conditions.add(hasPriority(filter.priority()));
		}
		if (filter.assigneeId() != null) {
			conditions.add(assignedTo(filter.assigneeId()));
		}
		if (filter.search() != null && !filter.search().isBlank()) {
			conditions.add(titleContains(filter.search()));
		}
		return Specification.allOf(conditions);
	}

	// WHERE project_id = ?
	public static Specification<Task> inProject(Long projectId) {
		return (task, query, cb) -> cb.equal(task.get("project").get("id"), projectId);
	}

	// AND status = ?
	public static Specification<Task> hasStatus(TaskStatus status) {
		return (task, query, cb) -> cb.equal(task.get("status"), status);
	}

	// AND priority = ?
	public static Specification<Task> hasPriority(TaskPriority priority) {
		return (task, query, cb) -> cb.equal(task.get("priority"), priority);
	}

	// AND assignee_id = ?
	public static Specification<Task> assignedTo(Long userId) {
		return (task, query, cb) -> cb.equal(task.get("assignee").get("id"), userId);
	}

	// AND lower(title) LIKE '%text%'  (case-insensitive "contains")
	public static Specification<Task> titleContains(String text) {
		String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
		return (task, query, cb) -> cb.like(cb.lower(task.get("title")), pattern, '\\');
	}

	// So a search for "100%" matches the text "100%" instead of treating % as a wildcard.
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
