package experimentalcode.erich.gdbscan;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
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

import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * The DBSCAN default core point predicate -- having at least {@link #minpts}
 * neighbors.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has Instance
 */
public class MinPtsCorePredicate implements CorePredicate {
  /**
   * The minpts parameter.
   */
  int minpts;

  /**
   * Default constructor.
   * 
   * @param minpts Minimum number of neighbors to be a core point.
   */
  public MinPtsCorePredicate(int minpts) {
    super();
    this.minpts = minpts;
  }

  @Override
  public Instance instantiate(Database rel) {
    return new Instance(minpts);
  }

  /**
   * Instance for a particular data set.
   * 
   * @author Erich Schubert
   */
  public class Instance implements CorePredicate.Instance {
    /**
     * The minpts parameter.
     */
    int minpts;

    /**
     * Constructor for this predicate.
     * 
     * @param minpts MinPts parameter
     */
    public Instance(int minpts) {
      super();
      this.minpts = minpts;
    }

    @Override
    public boolean isCorePoint(DBID point, DBIDs neighbors) {
      return neighbors.size() >= minpts;
    }
  }

  /**
   * Parameterization class
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Minpts value
     */
    int minpts;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      // Get the minpts parameter
      IntParameter minptsP = new IntParameter(de.lmu.ifi.dbs.elki.algorithm.clustering.DBSCAN.MINPTS_ID);
      if(config.grab(minptsP)) {
        minpts = minptsP.getValue();
      }
    }

    @Override
    protected MinPtsCorePredicate makeInstance() {
      return new MinPtsCorePredicate(minpts);
    }
  }
}