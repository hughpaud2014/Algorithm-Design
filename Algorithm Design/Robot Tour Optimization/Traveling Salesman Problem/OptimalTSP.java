/*Author: Paul Hughes
 * Date: 07/18/2024
 * Description: Traveling 
 * Problem: Robot Tour Optimization
 * Input: A set S of n points in the plane.
 * Output: What is the shortest cycle that visits each point in the set S?
 */


public class OptimalTSP {
    public static void main(String[] args) {
        // Define the graph as a 2D array representing the distances between cities
        int[][] graph = {
                { 0, 10, 15, 20 },
                { 10, 0, 35, 25 },
                { 15, 35, 0, 30 },
                { 20, 25, 30, 0 }
        };

        // Create an array to store the optimal path
        int[] path = new int[graph.length];

        // Create a boolean array to keep track of visited cities
        boolean[] visited = new boolean[graph.length];

        // Initialize the minimum cost to a very large value
        int minCost = Integer.MAX_VALUE;

        // Start the path from the first city (city 0)
        path[0] = 0;

        // Mark the first city as visited
        visited[0] = true;

        // Call the tsp() method to find the optimal TSP path
        tsp(graph, path, visited, 1, 0, minCost);

        // Print the optimal TSP path
        System.out.println("Optimal TSP path: ");
        for (int i = 0; i < graph.length; i++) {
            System.out.print(path[i] + " ");
        }
        System.out.println();

        // Print the optimal TSP cost
        System.out.println("Optimal TSP cost: " + minCost);
    }

    // Recursive method to find the optimal TSP path
    public static void tsp(int[][] graph, int[] path, boolean[] visited, int pos, int cost, int minCost) {
        // Base case: if all cities have been visited
        if (pos == graph.length) {
            // Check if there is a path from the last city back to the starting city
            if (graph[path[pos - 1]][path[0]] != 0) {
                // Calculate the current cost by adding the cost of the last edge
                int currentCost = cost + graph[path[pos - 1]][path[0]];

                // Check if the current cost is less than the minimum cost
                if (currentCost < minCost) {
                    // If so, update the minimum cost and copy the path to the optimal path
                    int[] optimalPath = new int[path.length];
                    System.arraycopy(path, 0, optimalPath, 0, path.length);
                    minCost = currentCost;
                }
            }
            return;
        }

        // Recursive case: try all possible cities as the next city in the path
        for (int i = 1; i < graph.length; i++) {
            if (!visited[i] && graph[path[pos - 1]][i] != 0) {
                // Mark the next city as visited and add it to the path
                visited[i] = true;
                path[pos] = i;

                // Recursively call tsp() with the updated path and cost
                tsp(graph, path, visited, pos + 1, cost + graph[path[pos - 1]][i], minCost);

                // Backtrack: mark the next city as unvisited
                visited[i] = false;
            }
        }
    }
}
