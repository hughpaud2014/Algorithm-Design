/*Author: Paul Hughes
 * Date: 07/17/2024
 * Description: Closest Pair
 * Problem: Robot Tour Optimization
 * Input: A set S of n points in the plane.
 * Output: What is the shortest cycle that visits each point in the set S?
 */



/**
 * This file contains the implementation of the ClosestPair class.
 * The ClosestPair class is responsible for finding the closest pair of points in a given set of points.
 * It uses the java.util package to access the necessary data structures and algorithms.
 */
 import java.util.*;

class Point {
    int x, y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

class ClosestPair {
    // Function to calculate the Euclidean distance between two points
    static double distance(Point p1, Point p2) {
        int dx = p1.x - p2.x;
        int dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Function to find the closest pair of points
    static double closestPair(Point[] points, int n) {
        // Sort the points based on x-coordinate
        Arrays.sort(points, (p1, p2) -> p1.x - p2.x);

        // Recursive function to find the closest pair of points
        return closestPairUtil(points, 0, n - 1);
    }

    // Recursive function to find the closest pair of points
    static double closestPairUtil(Point[] points, int left, int right) {
        // Base case: If there are less than 3 points, calculate the distance directly
        if (right - left <= 2) {
            double minDist = Double.MAX_VALUE;
            for (int i = left; i <= right; i++) {
                for (int j = i + 1; j <= right; j++) {
                    double dist = distance(points[i], points[j]);
                    minDist = Math.min(minDist, dist);
                }
            }
            return minDist;
        }    

        // Divide the points into two halves
        int mid = (left + right) / 2;
        Point midPoint = points[mid];

        // Recursively find the minimum distance in each half
        double distLeft = closestPairUtil(points, left, mid);
        double distRight = closestPairUtil(points, mid + 1, right);
        double minDist = Math.min(distLeft, distRight);

        // Find the points that are within the minimum distance from the middle line
        List<Point> strip = new ArrayList<>();
        for (int i = left; i <= right; i++) {
            if (Math.abs(points[i].x - midPoint.x) < minDist) {
                strip.add(points[i]);
            }
        }

        // Find the closest pair of points in the strip
        double stripMinDist = minDist;
        for (int i = 0; i < strip.size(); i++) {
            for (int j = i + 1; j < strip.size() && (strip.get(j).y - strip.get(i).y) < stripMinDist; j++) {
                double dist = distance(strip.get(i), strip.get(j));
                stripMinDist = Math.min(stripMinDist, dist);
            }
        }

        // Return the minimum distance
        return Math.min(minDist, stripMinDist);
    }

    // Main method to test the Closest Pair Algorithm
    public static void main(String[] args) {
        Point[] points = { new Point(2, 3), new Point(12, 30), new Point(40, 50), new Point(5, 1),
                new Point(12, 10), new Point(3, 4) };

        int n = points.length;
        double minDist = closestPair(points, n);

        System.out.println("The minimum distance between two points is: " + minDist);
    }
}