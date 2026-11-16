package com.ratana.task_management_api.task.enums;

/** How a task given to several people (assigneeIds / assignWholeDepartment) is set up. */
public enum AssignmentMode {
    /**
     * Everyone does their own copy: a group task with one member subtask per person, each with
     * its own submission and review. Closes when every member is approved. The default.
     */
    INDIVIDUAL,
    /**
     * One shared piece of work: a single task (first person = primary assignee, the rest are
     * collaborators). Any of them can submit; one approval completes it for everyone.
     */
    SHARED
}
