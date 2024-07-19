# Traveling Salesman Problem

The Traveling Salesman Problem (TSP) is a classic optimization problem in computer science. It involves finding the shortest possible route that a salesman can take to visit a set of cities and return to the starting city, visiting each city exactly once.

## Problem Statement

Given a list of cities and the distances between each pair of cities, the goal is to find the shortest possible route that visits each city exactly once and returns to the starting city.

## Algorithm

The most common algorithm used to solve the TSP is the **Brute Force** approach, which involves generating all possible permutations of the cities and calculating the total distance for each permutation. The shortest route is then selected as the solution.

## Example

Let's consider a simple example with 4 cities: A, B, C, and D. The distances between the cities are as follows:

- A to B: 10
- A to C: 15
- A to D: 20
- B to C: 35
- B to D: 25
- C to D: 30

To solve this problem using the Brute Force approach, we generate all possible permutations of the cities:

- ABCD
- ABDC
- ACBD
- ACDB
- ADBC
- ADCB
- BACD
- BADC
- BCAD
- BCDA
- BDAC
- BDCA
- CABD
- CADB
- CBAD
- CBDA
- CDAB
- CDBA
- DABC
- DACB
- DBAC
- DBCA
- DCAB
- DCBA

We then calculate the total distance for each permutation:

- ABCD: 10 + 35 + 30 + 20 = 95
- ABDC: 10 + 25 + 15 + 20 = 70
- ACBD: 15 + 35 + 25 + 20 = 95
- ACDB: 15 + 30 + 10 + 25 = 80
- ADBC: 20 + 35 + 10 + 25 = 90
- ADCB: 20 + 30 + 15 + 35 = 100
- BACD: 35 + 10 + 30 + 20 = 95
- BADC: 35 + 25 + 15 + 20 = 95
- BCAD: 35 + 10 + 25 + 30 = 100
- BCDA: 35 + 30 + 10 + 15 = 90
- BDAC: 25 + 10 + 35 + 20 = 90
- BDCA: 25 + 30 + 15 + 10 = 80
- CABD: 30 + 10 + 35 + 25 = 100
- CADB: 30 + 15 + 10 + 35 = 90
- CBAD: 30 + 10 + 25 + 15 = 80
- CBDA: 30 + 25 + 15 + 10 = 80
- CDAB: 30 + 15 + 35 + 10 = 90
- CDBA: 30 + 25 + 10 + 15 = 80
- DABC: 20 + 35 + 30 + 10 = 95
- DACB: 20 + 15 + 35 + 30 = 100
- DBAC: 25 + 10 + 35 + 30 = 100
- DBCA: 25 + 30 + 10 + 35 = 100
- DCAB: 25 + 15 + 10 + 35 = 85
- DCBA: 25 + 30 + 35 + 10 = 100

The shortest route is CBDA, with a total distance of 80.

## Conclusion

The Traveling Salesman Problem is a challenging optimization problem with various algorithms to solve it. The Brute Force approach is simple but becomes impractical for larger problem instances due to its exponential time complexity. Other algorithms, such as the **Nearest Neighbor** and **Genetic Algorithms**, are commonly used to find approximate solutions efficiently.
