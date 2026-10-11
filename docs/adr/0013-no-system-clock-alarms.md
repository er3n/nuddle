# 0013 No alarms in the system Clock app

Status: Accepted (2026-10-10).

## Decision
The app never uses `AlarmClock.ACTION_SET_ALARM` to put alarms in the system Clock app.

## Reasons
- No "Done" action and no learning: the alarm does not come back to us.
- Alarms may not be cancellable by us when the sender cancels the task.
- It would show our internal plan as visible alarms, which contradicts the hidden plan principle.

All pokes are our own alarms via `AlarmManager.setAlarmClock`.
