# Earliest Job First Algorithm

The Earliest Job First (EJF) algorithm is a scheduling algorithm used in operating systems to determine the order in which jobs should be executed. It is a non-preemptive algorithm, meaning that once a job starts executing, it will continue until completion.

## How it works

The EJF algorithm prioritizes jobs based on their arrival time. The job with the earliest arrival time is executed first, followed by the next earliest, and so on. If two jobs have the same arrival time, the one with the shortest execution time is selected first.

## Example

Consider the following set of jobs with their arrival times and execution times:

| Job | Arrival Time | Execution Time |
|-----|--------------|----------------|
| A   | 0            | 5              |
| B   | 1            | 3              |
| C   | 2            | 2              |
| D   | 3            | 4              |

Using the EJF algorithm, the order in which the jobs will be executed is as follows:

1. Job A (arrival time: 0, execution time: 5)
2. Job B (arrival time: 1, execution time: 3)
3. Job C (arrival time: 2, execution time: 2)
4. Job D (arrival time: 3, execution time: 4)

## Advantages and Disadvantages

The EJF algorithm has the advantage of minimizing the average waiting time for jobs, as it prioritizes the earliest arriving jobs. However, it may lead to starvation for jobs with higher execution times if a large number of short jobs keep arriving.
