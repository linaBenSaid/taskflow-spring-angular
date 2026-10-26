package com.taskflow.dto;

import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Optional;

// Body of PATCH /api/tasks/{id}: only the fields that are sent change.
//
// title, status, priority can't be emptied, so null means "not sent, keep the current value".
// description, dueDate, assigneeId CAN be emptied, so they need three states. Optional gives us that:
//   field missing from the JSON  -> null               -> keep the current value
//   "assigneeId": null           -> Optional.empty()   -> clear it (unassign)
//   "assigneeId": 7              -> Optional.of(7)     -> set it
// Example: dragging a card on the Kanban board sends just {"status": "DONE"}.
//
// A class with setters, not a record: Jackson fills every record field, turning a missing Optional into
// Optional.empty() (indistinguishable from an explicit null). Setters are only called for fields that are sent.
@Getter
@Setter
@NoArgsConstructor
public class UpdateTaskRequest {

	@Size(max = 200)
	@Pattern(regexp = ".*\\S.*", message = "must not be blank")
	private String title;

	private Optional<@Size(max = 5000) String> description;

	private TaskStatus status;

	private TaskPriority priority;

	private Optional<LocalDate> dueDate;

	private Optional<Long> assigneeId;
}
