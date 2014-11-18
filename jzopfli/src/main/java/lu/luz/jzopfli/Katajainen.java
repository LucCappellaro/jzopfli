/*
Copyright 2011 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Author: lode.vandevenne@gmail.com (Lode Vandevenne)
Author: jyrki.alakuijala@gmail.com (Jyrki Alakuijala)
*/
package lu.luz.jzopfli;
/**
Bounded package merge algorithm, based on the paper
"A Fast and Space-Economical Algorithm for Length-Limited Coding
Jyrki Katajainen, Alistair Moffat, Andrew Turpin".
*/

//#include "katajainen.h"
//#include <assert.h>
import java.util.*;//#include <stdlib.h>
class Katajainen extends KatajainenH{
//typedef struct Node Node;

/**
Nodes forming chains. Also used to represent leaves.
*/
private static class Node {
  int weight;  /* Total weight (symbol count) of this chain. */
  Node tail;  /* Previous node(s) of this chain, or 0 if none. */
  int count;  /* Leaf symbol index, or number of leaves before this chain. */
  boolean inuse;  /* Tracking for garbage collection. */
public String toString(){return "Node[w="+weight+",c="+count+",u="+inuse+"]";}};

/**
Memory pool for nodes.
*/
private static class NodePool {
  Node[] nodes;  /* The pool. */
  int next;  /* Pointer to a possibly free node in the pool. */
  int size;  /* Size of the memory pool. */
} //NodePool;

/**
Initializes a chain node with the given values and marks it as in use.
*/
private static void InitNode(int weight, int count, Node tail, Node node) {
  node.weight = weight;
  node.count = count;
  node.tail = tail;
  node.inuse = true;
}

/**
Finds a free location in the memory pool. Performs garbage collection if needed.
lists: If given, used to mark in-use nodes during garbage collection.
maxbits: Size of lists.
pool: Memory pool to get free node from.
*/
private static Node GetFreeNode(Node[][] lists, int maxbits, NodePool pool) {
  for (;;) {
    if (pool.next >= pool.size) {
      /* Garbage collection. */
      int i;
      for (i = 0; i < pool.size; i++) {
        pool.nodes[i].inuse = false;
      }
      if (lists!=null) {
        for (i = 0; i < maxbits * 2; i++) {
          Node node;
          for (node = lists[i / 2][i % 2]; node!=null; node = node.tail) {
            node.inuse = true;
          }
        }
      }
      pool.next = 0;
    }
    if (!pool.nodes[pool.next].inuse) break;  /* Found one. */
    pool.next++;
  }
  return pool.nodes[pool.next++];
}


/**
Performs a Boundary Package-Merge step. Puts a new chain in the given list. The
new chain is, depending on the weights, a leaf or a combination of two chains
from the previous list.
lists: The lists of chains.
maxbits: Number of lists.
leaves: The leaves, one per symbol.
numsymbols: Number of leaves.
pool: the node memory pool.
index: The index of the list in which a new chain or leaf is required.
final: Whether this is the last time this function is called. If it is then it
  is no more needed to recursively call self.
*/
private static void BoundaryPM(Node[][] lists, int maxbits,
    Node[] leaves, int numsymbols, NodePool pool, int index, boolean finaL) {
  Node newchain;
  Node oldchain;
  int lastcount = lists[index][1].count;  /* Count of last chain of list. */

  if (index == 0 && lastcount >= numsymbols) return;

  newchain = GetFreeNode(lists, maxbits, pool);
  oldchain = lists[index][1];

  /* These are set up before the recursive calls below, so that there is a list
  pointing to the new node, to let the garbage collection know it's in use. */
  lists[index][0] = oldchain;
  lists[index][1] = newchain;

  if (index == 0) {
    /* New leaf node in list 0. */
    InitNode(leaves[lastcount].weight, lastcount + 1, null, newchain);
  } else {
    int sum = lists[index - 1][0].weight + lists[index - 1][1].weight;
    if (lastcount < numsymbols && sum > leaves[lastcount].weight) {
      /* New leaf inserted in list, so count is incremented. */
      InitNode(leaves[lastcount].weight, lastcount + 1, oldchain.tail,
          newchain);
    } else {
      InitNode(sum, lastcount, lists[index - 1][1], newchain);
      if (!finaL) {
        /* Two lookahead chains of previous list used up, create new ones. */
        BoundaryPM(lists, maxbits, leaves, numsymbols, pool, index - 1, false);
        BoundaryPM(lists, maxbits, leaves, numsymbols, pool, index - 1, false);
      }
    }
  }
}

/**
Initializes each list with as lookahead chains the two leaves with lowest
weights.
*/
private static void InitLists(
    NodePool pool, Node[] leaves, int maxbits, Node[][] lists) {
  int i;
  Node node0 = GetFreeNode(null, maxbits, pool);
  Node node1 = GetFreeNode(null, maxbits, pool);
  InitNode(leaves[0].weight, 1, null, node0);
  InitNode(leaves[1].weight, 2, null, node1);
  for (i = 0; i < maxbits; i++) {
    lists[i][0] = node0;
    lists[i][1] = node1;
  }
}

/**
Converts result of boundary package-merge to the bitlengths. The result in the
last chain of the last list contains the amount of active leaves in each list.
chain: Chain to extract the bit length from (last chain from last list).
*/
private static void ExtractBitLengths(Node chain, Node[] leaves, int[] bitlengths) {
  Node node;
  for (node = chain; node!=null; node = node.tail) {
    int i;
    for (i = 0; i < node.count; i++) {
      bitlengths[leaves[i].count]++;
    }
  }
}

/**
Comparator for sorting the leaves. Has the function signature for qsort.
*/
private static LeafComparator leafComparator=new LeafComparator(); private static class LeafComparator implements Comparator<Node>{public int compare(Node a, Node b) {
  return ((Node)a).weight - ((Node)b).weight;
}}

public static boolean ZopfliLengthLimitedCodeLengths(
    int[] frequencies, int n, int maxbits, int[] bitlengths) {
  NodePool pool = new NodePool();
  int i;
  int numsymbols = 0;  /* Amount of symbols with frequency > 0. */
  int numBoundaryPMRuns;

  /* Array of lists of chains. Each list requires only two lookahead chains at
  a time, so each list is a array of two Node*'s. */
  Node[][] lists;

  /* One leaf per symbol. Only numsymbols leaves will be used. */
  Node[] leaves = new Node[n];

  /* Initialize all bitlengths at 0. */
  for (i = 0; i < n; i++) {
    bitlengths[i] = 0;
  }

  /* Count used symbols and place them in the leaves. */
  for (i = 0; i < n; i++) {
    if (frequencies[i]!=0) { leaves[numsymbols]=new Node();
      leaves[numsymbols].weight = frequencies[i];
      leaves[numsymbols].count = i;  /* Index of symbol this leaf represents. */
      numsymbols++;
    }
  }

  /* Check special cases and error conditions. */
  if ((1 << maxbits) < numsymbols) {
//    free(leave);
    return true;  /* Error, too few maxbits to represent symbols. */
  }
  if (numsymbols == 0) {
//    free(leaves);
    return false;  /* No symbols at all. OK. */
  }
  if (numsymbols == 1) {
    bitlengths[leaves[0].count] = 1;
//    free(leaves);
    return false;  /* Only one symbol, give it bitlength 1, not 0. OK. */
  }

  /* Sort the leaves from lightest to heaviest. */
  Arrays.sort(leaves, 0, numsymbols, leafComparator);

  /* Initialize node memory pool. */
  pool.size = 2 * maxbits * (maxbits + 1);
  pool.nodes = new Node[pool.size];
  pool.next = 0;
  for (i = 0; i < pool.size; i++) {pool.nodes[i]=new Node();
    pool.nodes[i].inuse = false;
  }

  lists = new Node[maxbits][2];
  InitLists(pool, leaves, maxbits, lists);

  /* In the last list, 2 * numsymbols - 2 active chains need to be created. Two
  are already created in the initialization. Each BoundaryPM run creates one. */
  numBoundaryPMRuns = 2 * numsymbols - 4;
  for (i = 0; i < numBoundaryPMRuns; i++) {
    boolean finaL = i == numBoundaryPMRuns - 1;
    BoundaryPM(lists, maxbits, leaves, numsymbols, pool, maxbits - 1, finaL);
  }

  ExtractBitLengths(lists[maxbits - 1][1], leaves, bitlengths);

//  free(lists);
//  free(leaves);
//  free(pool.nodes);
  return false;  /* OK. */
}
}