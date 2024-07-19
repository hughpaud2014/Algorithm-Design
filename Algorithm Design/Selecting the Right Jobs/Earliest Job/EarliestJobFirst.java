import java.util.Arrays;

/*Author: Paul Hughes
 * Date: 07/19/2024
 * Description: Selecting the right jobs
 * Problem: Movie Scheduling Problem
 * Input: A set of I of n intervals on the line
 * Output: What is the largest subset of mutually non-overlapping intervals that can be selected from I?
 */

public class EarliestJobFirst {

    // Method to find the minimum arrival time among the jobs
    public static int findMinimumArrivalTime(int[] arrivalTimes) {
        int minArrivalTime = Integer.MAX_VALUE;

        // Iterate through the array of arrival times
        for (int arrivalTime : arrivalTimes) {
            if (arrivalTime < minArrivalTime) {
                minArrivalTime = arrivalTime;
            }
        }

        return minArrivalTime;
    }

    // Method to find the index of the job with the earliest arrival time
    public static int findEarliestJobIndex(int[] arrivalTimes, int minArrivalTime) {
        for (int i = 0; i < arrivalTimes.length; i++) {
            if (arrivalTimes[i] == minArrivalTime) {
                return i;
            }
        }

        return -1; // Return -1 if no job with the earliest arrival time is found
    }

    // Method to sort the jobs based on their arrival times in ascending order
    public static void sortJobs(int[] arrivalTimes, int[] jobIds) {
        // Create a copy of the arrival times array to preserve the original order of
        // jobIds
        int[] sortedArrivalTimes = Arrays.copyOf(arrivalTimes, arrivalTimes.length);
        Arrays.sort(sortedArrivalTimes);

        // Rearrange the jobIds array based on the sorted arrival times
        for (int i = 0; i < jobIds.length; i++) {
            int index = findEarliestJobIndex(arrivalTimes, sortedArrivalTimes[i]);
            int temp = jobIds[i];
            jobIds[i] = jobIds[index];
            jobIds[index] = temp;
        }
    }

    // Method to find the largest subset of mutually non-overlapping intervals
    public static int[] findLargestSubset(int[] arrivalTimes, int[] jobIds) {
        int[] largestSubset = new int[jobIds.length];
        int subsetSize = 0;
        int previousEndTime = Integer.MIN_VALUE;

        for (int i = 0; i < jobIds.length; i++) {
            int jobId = jobIds[i];
            int arrivalTime = arrivalTimes[jobId - 1];

            if (arrivalTime >= previousEndTime) {
                largestSubset[subsetSize] = jobId;
                subsetSize++;
                previousEndTime = arrivalTime + 1; // Assuming each job has a duration of 1 unit
            }
        }

        return Arrays.copyOf(largestSubset, subsetSize);
    }

    // Main method to demonstrate the Earliest Job First algorithm
    public static void main(String[] args) {
        int[] arrivalTimes = { 2, 4, 1, 3 }; // Example arrival times
        int[] jobIds = { 1, 2, 3, 4 }; // Example job IDs

        // Find the minimum arrival time among the jobs
        int minArrivalTime = findMinimumArrivalTime(arrivalTimes);

        // Sort the jobs based on their arrival times
        sortJobs(arrivalTimes, jobIds);

        // Find the largest subset of mutually non-overlapping intervals
        int[] largestSubset = findLargestSubset(arrivalTimes, jobIds);

        // Print the sorted job order
        System.out.println("Job order based on Earliest Job First algorithm:");
        for (int jobId : largestSubset) {
            System.out.print(jobId + " ");
        }
    }
}
