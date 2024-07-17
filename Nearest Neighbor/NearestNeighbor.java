/*Author: Paul Hughes
 * Date: 07/16/2024
 * Description: Nearest
 * Problem: Robot Tour Optimization
 * Input: A set S of n points in the plane.
 * Output: What is the shortest cycle that visits each point in the set S?
 */

public class NearestNeighbor {
    public static void main(String[] args) {
        int[][] matrix = {
                { 0, 1, 2, 3, 4 },
                { 1, 0, 5, 6, 7 },
                { 2, 5, 0, 8, 9 },
                { 3, 6, 8, 0, 10 },
                { 4, 7, 9, 10, 0 }
        };

        int[] path = nearestNeighbor(matrix);
        for (int i = 0; i < path.length; i++) {
            System.out.print(path[i] + " ");
        }
    }

    public static int[] nearestNeighbor(int[][] matrix) {
        int n = matrix.length;
        int[] path = new int[n];
        boolean[] visited = new boolean[n];

        path[0] = 0;
        visited[0] = true;

        for (int i = 1; i < n; i++) {
            int min = Integer.MAX_VALUE;
            int index = -1;

            for (int j = 0; j < n; j++) {
                if (!visited[j] && matrix[path[i - 1]][j] < min) {
                    min = matrix[path[i - 1]][j];
                    index = j;
                }
            }

            path[i] = index;
            visited[index] = true;
        }

        return path;
    }
}
