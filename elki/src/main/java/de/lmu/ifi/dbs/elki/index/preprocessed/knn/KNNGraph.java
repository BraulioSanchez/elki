package de.lmu.ifi.dbs.elki.index.preprocessed.knn;
/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2016
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Random;

import de.lmu.ifi.dbs.elki.database.datastore.memory.MapStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDMIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DoubleDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.HashSetModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.KNNHeap;
import de.lmu.ifi.dbs.elki.database.ids.KNNList;
import de.lmu.ifi.dbs.elki.database.query.DatabaseQuery;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.IndefiniteProgress;
import de.lmu.ifi.dbs.elki.logging.statistics.DoubleStatistic;
import de.lmu.ifi.dbs.elki.logging.statistics.LongStatistic;
import de.lmu.ifi.dbs.elki.logging.statistics.StringStatistic;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.RandomParameter;
import de.lmu.ifi.dbs.elki.utilities.random.RandomFactory;

public class KNNGraph<O> extends AbstractMaterializeKNNPreprocessor<O> {
  /**
   * Logger
   */
  private static final Logging LOG = Logging.getLogger(KNNGraph.class);

  
  /**
   * Random generator
   */
  private final RandomFactory rnd;
  
  /**
   * total distance computations
   */
  private double counter_all=0.0;
  
  /**
   * early termination parameter
   */
  private double delta = 0.001;
  
  /**
   * sample rate
   */
  private double rho = 1.0;
  
  /**
   * Constructor.
   *
   * @param relation Relation to index
   * @param distanceFunction distance function
   * @param k k
   * @param rnd Random generator
   */
  public KNNGraph(Relation<O> relation, DistanceFunction<? super O> distanceFunction, int k, RandomFactory rnd, double delta, double rho) {
    super(relation, distanceFunction, k);
    this.rnd = rnd;
    this.delta = delta;
    this.rho = rho;
  }

  @Override
  protected void preprocess() {
    final long starttime = System.currentTimeMillis();
    DistanceQuery<O> distanceQuery = relation.getDistanceQuery(distanceFunction);
    
    storage = new MapStore<KNNList>();
    MapStore<KNNHeap> store = new MapStore<KNNHeap>();
    
    MapStore<HashSetModifiableDBIDs> newNeighborHash = new MapStore<HashSetModifiableDBIDs>();    
    
    IndefiniteProgress progress = LOG.isVerbose() ? new IndefiniteProgress("KNNGraph iteration", LOG) : null;
    
    Random random = rnd.getSingleThreadedRandom();
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      DBID id = DBIDUtil.deref(iditer);
      
      final DBIDs sample = DBIDUtil.randomSample(relation.getDBIDs(), k, random);
      KNNHeap heap = DBIDUtil.newHeap(k);
      HashSetModifiableDBIDs newNeighbors = DBIDUtil.newHashSet(k);
      for (DBIDIter siter = sample.iter(); siter.valid(); siter.advance()){
        heap.insert(distanceQuery.distance(iditer, siter), siter);
        newNeighbors.add(siter);
      }
      store.put(id,heap);
      newNeighborHash.put(id, newNeighbors);
    }
    
    int size = relation.size();
    int counter = k * size;
    
    while (counter >= delta*k*size){
      LOG.incrementProcessed(progress);
      counter = 0;
      //iterate through dataset
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        //for every pair of neighbors do
        //calculate also reverse neighbors separately for old and new
        HashSetModifiableDBIDs newNeighbors = newNeighborHash.get(iditer);
        DBID id = DBIDUtil.deref(iditer);
        KNNHeap heap = store.get(iditer);
        HashSetModifiableDBIDs allNewNeighbors = DBIDUtil.newHashSet();
        HashSetModifiableDBIDs oldNeighbors = DBIDUtil.newHashSet();
        for (DoubleDBIDListIter heapiter = heap.unorderedIterator(); heapiter.valid(); heapiter.advance()){
          if (newNeighbors.contains(heapiter)){
            allNewNeighbors.add(heapiter);
          }
          else{
            oldNeighbors.add(heapiter);
          }
        }
        
        int items = (int) Math.round(rho*k);
        
        //Sampling
        final DBIDs sampleNew = DBIDUtil.randomSample(allNewNeighbors, Math.min(items, allNewNeighbors.size()), random);        
        DBIDs newRev = newReverse(id, newNeighborHash);
        
        //determine the sampled new neighbors (also reserve)
        HashSetModifiableDBIDs usedNew = DBIDUtil.newHashSet(sampleNew);
        usedNew.addDBIDs(DBIDUtil.randomSample(newRev, Math.min(items, newRev.size()), random));
        
        //determine old neighbors and sampled reverse ones
        DBIDs oldRev = oldReverse(id, newNeighborHash, store);
        oldNeighbors.addDBIDs(DBIDUtil.randomSample(oldRev, Math.min(items, oldRev.size()), random));
        
        for(DBIDMIter niter = usedNew.iter(); niter.valid(); niter.advance()) {
          newNeighbors.remove(niter);
          HashSetModifiableDBIDs niternew = newNeighborHash.get(niter);
          KNNHeap neighbors = store.get(niter);
          for(DBIDMIter niter2 = usedNew.iter(); niter2.valid(); niter2.advance()) {
            newNeighbors.remove(niter2);
            HashSetModifiableDBIDs niternew2 = newNeighborHash.get(niter2);
            KNNHeap neighbors2 = store.get(niter2);
            if (DBIDUtil.compare(niter, niter2)<0){
              int add = add (neighbors, niter, niter2);
              if (add > 0){
                counter++;
                niternew.add(niter2);
              }
              int add2 = add (neighbors2, niter2, niter);
              if (add2 > 0){
                counter++;
                niternew2.add(niter);
              }
            }
            
            store.put(niter2, neighbors2);
            newNeighborHash.put(niter2, niternew2);
          }
          store.put(niter, neighbors);
          newNeighborHash.put(niter, niternew);
          for(DBIDMIter niter2 = oldNeighbors.iter(); niter2.valid(); niter2.advance()) {
            HashSetModifiableDBIDs niternew2 = newNeighborHash.get(niter2);
            KNNHeap neighbors2 = store.get(niter2);
            if (DBIDUtil.compare(niter, niter2)!=0){
              int add = add (neighbors, niter, niter2);
              if (add > 0){
                counter++;
                niternew.add(niter2);
              }
              int add2 = add (neighbors2, niter2, niter);
              if (add2 > 0){
                counter++;
                niternew2.add(niter);
              }
            }
            store.put(niter2, neighbors2);
            newNeighborHash.put(niter2, niternew2);
          }
          store.put(niter, neighbors);
          newNeighborHash.put(niter, niternew);
        }
      }
      counter_all+=counter;
      if (LOG.isStatistics()){
        LOG.statistics(new StringStatistic("Distance computations in this iteration",Integer.toString(counter)));
      }

    }
    //convert store to storage
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      KNNHeap heap = store.get(iditer);
      KNNList list = heap.toKNNList();
      storage.put(iditer, list);
    }
    LOG.setCompleted(progress);
    final long end = System.currentTimeMillis();
    if(LOG.isStatistics()) {
      LOG.statistics(new LongStatistic(this.getClass().getCanonicalName() + ".construction-time.ms", end - starttime));
    }
  }

  /**
   * 
   * @param id - object for which reverse neighbors are calculated
   * @param newNeighborHash - hash with new neighbors for every object
   * @return reverse new neighbors
   */
  private HashSetModifiableDBIDs newReverse(DBID id, MapStore<HashSetModifiableDBIDs> newNeighborHash) {
    HashSetModifiableDBIDs rev = DBIDUtil.newHashSet(k);
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      HashSetModifiableDBIDs neighbors = newNeighborHash.get(iditer);
      if (neighbors.contains(id)){
        rev.add(iditer);
      }
    }
    return rev;
  }
  
  /**
   * 
   * @param id - object for which reverse neighbors are calculated
   * @param newNeighborHash - hash with new neighbors for every object
   * @param store - hash with all neighbors for every object
   * @return reverse old neighbors
   */
  private DBIDs oldReverse(DBID id, MapStore<HashSetModifiableDBIDs> newNeighborHash, MapStore<KNNHeap> store) {
    HashSetModifiableDBIDs rev = DBIDUtil.newHashSet(k);
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      HashSetModifiableDBIDs neighbors = newNeighborHash.get(iditer);
      KNNHeap allNeighbors = store.get(iditer);
      for (DoubleDBIDListIter heapiter = allNeighbors.unorderedIterator(); heapiter.valid(); heapiter.advance()){
        if (id.compareTo(heapiter)==0 && !neighbors.contains(id)){
          rev.add(iditer);
        }
      }
    }
    return rev;
  }
  /**
   * 
   * add nniter to iditer-hash newNeighbors
   * 
   * @param newNeighbors
   * @param iditer
   * @param nniter
   * @return
   */
  private int add(KNNHeap newNeighbors, DBIDRef iditer, DBIDRef nniter) {
    int ret = 0;
    //see if actual object is already contained in hash
    boolean contained=false;
    for (DoubleDBIDListIter heapiter = newNeighbors.unorderedIterator(); heapiter.valid(); heapiter.advance()){                 
      if (DBIDUtil.compare(heapiter, nniter)==0){
        contained=true;
      }
    }
    if (!contained){
      //calculate similarity of v and u2
      double distance = distanceQuery.distance(iditer,nniter);
      double newDistance = newNeighbors.insert(distance,nniter);
      if (distance <= newDistance){
        ret++;
      }
    }
    return ret;
  }

  private String print(KNNHeap heap) {
    String s ="";
    for (DoubleDBIDListIter heapiter = heap.unorderedIterator(); heapiter.valid(); heapiter.advance()){
      s+=DBIDUtil.deref(heapiter)+"\t";
    }
    return s;
  }



  @Override
  protected Logging getLogger() {
    return LOG;
  }
  
  @Override
  public void logStatistics() {
    double size = (double) relation.size();
    if (LOG.isStatistics()){
      LOG.statistics(new DoubleStatistic("Scan rate",counter_all/(size*(size-1.0)/2.0)));
    }
  }

  @Override
  public String getLongName() {
    return "NNDescent kNN";
  }

  @Override
  public String getShortName() {
    return "nn-descent-knn";
  }
  
  @Override
  public KNNQuery<O> getKNNQuery(DistanceQuery<O> distanceQuery, Object... hints) {
    for(Object hint : hints) {
      if(DatabaseQuery.HINT_EXACT.equals(hint)) {
        return null;
      }
    }
    return super.getKNNQuery(distanceQuery, hints);
  }
  
  public static class Factory<O> extends AbstractMaterializeKNNPreprocessor.Factory<O> {
    /**
     * Random generator
     */
    private final RandomFactory rnd;

    /**
     * early termination parameter
     */
    private final double delta;
    
    /**
     * sample rate
     */
    private final double rho;
    
    /**
     * Constructor.
     *
     * @param k K
     * @param distanceFunction distance function
     * @param rnd Random generator
     */
    public Factory(int k, DistanceFunction<? super O> distanceFunction, RandomFactory rnd, double delta, double rho) {
      super(k, distanceFunction);
      this.rnd = rnd;
      this.delta = delta;
      this.rho = rho;
    }

    @Override
    public KNNGraph<O> instantiate(Relation<O> relation) {
      return new KNNGraph<>(relation, distanceFunction, k, rnd, delta, rho);
    }

    /**
     * Parameterization class
     *
     * @author Erich Schubert
     *
     * @apiviz.exclude
     *
     * @param <O> Object type
     */
    public static class Parameterizer<O> extends AbstractMaterializeKNNPreprocessor.Factory.Parameterizer<O> {
      /**
       * Random number generator seed.
       *
       * <p>
       * Key: {@code -knngraph.seed}
       * </p>
       */
      public static final OptionID SEED_ID = new OptionID("knngraph.seed", "The random number seed.");
      
      /**
       * Early termination parameter.
       */
      public static final OptionID DELTA_ID = new OptionID("knngraph.delta", "The early termination parameter.");
      
      /**
       * Sample rate.
       */
      public static final OptionID RHO_ID = new OptionID("knngraph.rho", "The sample rate parameter");
      
      /**
       * Random generator
       */
      private RandomFactory rnd;
      
      /**
       * early termination parameter
       */
      private double delta;
      
      /**
       * sample rate
       */
      private double rho;
      
      @Override
      protected void makeOptions(Parameterization config) {
        super.makeOptions(config);
        RandomParameter rndP = new RandomParameter(SEED_ID);
        if(config.grab(rndP)) {
          rnd = rndP.getValue();
        }
        DoubleParameter deltaP = new DoubleParameter(DELTA_ID, 0.001);
        if(config.grab(deltaP)) {
          delta = deltaP.getValue();
        }
        DoubleParameter rhoP = new DoubleParameter(RHO_ID, 1);
        if (config.grab(rhoP)){
          rho = rhoP.getValue();
        }
      }

      @Override
      protected KNNGraph.Factory<O> makeInstance() {
        return new KNNGraph.Factory<>(k, distanceFunction, rnd, delta, rho);
      }
    }
  }
}


