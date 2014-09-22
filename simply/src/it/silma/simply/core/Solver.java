/**
 * 
 */
package it.silma.simply.core;

import it.silma.simply.main.Simply;
import it.silma.simply.utils.Constants;

import java.awt.Color;

import javax.swing.BorderFactory;

/**
 * Implementa il metodo del simplesso compattandolo in un'unica classe.
 * 
 * @author Silma
 * 
 */
class Solver {

    // Interfaccia del solutore
    protected SolverFrame solverData;

    /** Numero delle variabili decisionali. */
    int variablesNumber;
    /** Numero dei vincoli. */
    int constraintNumber;
    /** Riga e colonna del pivot. */
    static int pvtRow = -1;
    /** Riga e colonna del pivot. */
    static int pvtCol = -1;

    /*
     * Utilizzo qui la sintassi di [Hillier - Lieberman, RO]: - B - baseMatrix -
     * Matrice delle variabili di base - cB - costsVector - Vettore di costo
     * ridotto - y - slackVector - Vettore delle variabili slack - A -
     * constraintMatrix - Matrice dei vincoli - b - baseVector - Vettore dei
     * termini noti (valori delle var. di base) - t - tableauFirstLine - Prima
     * riga del tableau - T - tableauNLines - Il resto del tableau
     */

    /** Coefficienti di costo ridotto e di slack nella t */
    Variable[] variables;
    /**
     * Soluzione di base ammissibile. Contiene i <strong>valori</strong> di
     * tutte le variabili (non i coefficienti).
     */
    protected float[] bfs;

    /** Numero di variabili slack. */
    int slackNumber;
    /** Numero di variabili surplus. */
    /** Numero di variabili slack. */
    int surplusNumber;
    /** Numero di variabili artificiali. */
    /** Numero di variabili slack. */
    int artificialNumber;

    /**
     * Tableau interno: qui vengono fatti i calcoli, le altre
     * <code>Matrix</code> sono solo per comodita'
     */
    protected Matrix tableau;

    /** Tipo del problema */
    Constants problemType;
    /** Tipi dei vincoli */
    Constants[] constraintTypes;

    /**
     * Inizializza un Solver con uno specificato numero di variabili, di vincoli
     * e un'origine dei dati.
     * 
     * @param vNumber
     *            Il numero di variabili.
     * @param cNumber
     *            Il numero di vincoli.
     * @param origin
     *            L'origine dei dati.
     */
    public Solver(final int vNumber, final int cNumber, final SolverFrame origin) {
        // Inizializzazioni
        variablesNumber = vNumber;
        constraintNumber = cNumber;
        solverData = origin;
        // Numero di variabili slack, surplus, artificiali
        slackNumber = solverData.sourceProblem.getSlackNumber();
        surplusNumber = solverData.sourceProblem.getSurplusNumber();
        artificialNumber = solverData.sourceProblem.getArtificialNumber();
        // Il numero di variabili (della BFS) e' sempre pari al numero di
        // variabili slack piu' il
        // numero di variabili artificiali. Se non e' un problema artificiale,
        // quest'ultimo e' 0.
        variables = new Variable[vNumber + slackNumber + surplusNumber + artificialNumber];
        bfs = new float[vNumber + slackNumber + surplusNumber + artificialNumber];

        // Inizializzazione del tipo di problema e dei vincoli
        problemType = solverData.sourceProblem.getProblemType();
        constraintTypes = solverData.sourceProblem.getConstraintTypes();
        // Inizializzazione del tableau interno e del passo
        doInitialTableau();
        // Imposto il primo passo. Se il problema e' semplice, e' subito il
        // controllo dell'ottimo.
        if (Constants.STATUS.equals(Constants.Simple))
            Constants.STEP = Constants.OptimalityTest;
        else
            // Se il problema e' artificiale, all'inizio bisogna modificare il
            // tableau.
            Constants.STEP = Constants.PreprocessPhaseOne;
    }

    private void doInitialTableau() {
        System.out.println(Constants.STATUS.toString());
        // Se il problema e' risolvibile normalmente:
        if (Constants.STATUS.equals(Constants.Simple)) {
            // Tableau interno: il primo, deriva dalla schermata iniziale
            tableau = solverData.sourceProblem.getInitialTableau();
            // Valori iniziali del vettore di costo ridotto e delle variabili
            // slack
            for (int i = 0; i < variablesNumber; i++) {
                float reducedCost = 0;
                if (solverData.sourceProblem.getProblemType().equals(Constants.Maximize))
                    reducedCost = -solverData.sourceProblem.objectiveCoefficientVector[i].getCoefficient();
                else
                    reducedCost = solverData.sourceProblem.objectiveCoefficientVector[i].getCoefficient();
                variables[i] = new Variable(i, reducedCost, false, false);
            }
            for (int i = variablesNumber; i < variablesNumber + constraintNumber; i++) {
                variables[i] = new Variable(i, 0, true, true);
                variables[i].rowId = i - variablesNumber;
            }
            // Ottengo la BFS iniziale
            bfs = getBFS();
        } else if (Constants.STATUS.equals(Constants.ArtificialPhaseOne)) {
            // Tableau interno: artificiale e appositamente creato
            tableau = solverData.sourceProblem.getArtificialTableau();
            // Valori iniziali del vettore di costo ridotto e delle variabili
            // slack
            for (int i = 0; i < variablesNumber; i++) {
                float reducedCost = 0;
                if (solverData.sourceProblem.getProblemType().equals(Constants.Maximize))
                    reducedCost = -solverData.sourceProblem.objectiveCoefficientVector[i].getCoefficient();
                else
                    reducedCost = solverData.sourceProblem.objectiveCoefficientVector[i].getCoefficient();
                variables[i] = new Variable(i, reducedCost, false, false);
            }
            // Valori iniziali di slack, surplus, artificiali
            final Constants[] cts = solverData.sourceProblem.getConstraintTypes();
            int y = 0, z = 0, b = 0; // Tengono traccia del numero di variabili
            // slack
            // o surplus, artificiali e in base
            for (final Constants ct : cts) {
                // Le variabili slack hanno coefficiente 0.
                if (ct.equals(Constants.LessThan)) {
                    variables[y + variablesNumber] = new Variable(y + variablesNumber, 0, true, true);
                    variables[y++ + variablesNumber].rowId = b++;
                }
                // Le variabili surplus hanno coefficiente 0, quella artificiali
                // 1.
                if (ct.equals(Constants.GreaterThan)) {
                    variables[y + variablesNumber] = new Variable(y++ + variablesNumber, 0, false, true);
                    variables[variablesNumber + slackNumber + surplusNumber + z] = new Variable(variablesNumber
                            + slackNumber + surplusNumber + z, 1, true, false);
                    variables[variablesNumber + slackNumber + surplusNumber + z++].rowId = b++;
                }
                if (ct.equals(Constants.Equality)) {
                    variables[variablesNumber + slackNumber + surplusNumber + z] = new Variable(variablesNumber
                            + slackNumber + surplusNumber + z, 1, true, false);
                    variables[variablesNumber + slackNumber + surplusNumber + z++].rowId = b++;
                }
            }
            // Ottengo la BFS iniziale
            bfs = getBFS();
        }
    }

    /**
     * Recupera la variabile con l'ID (o, che e' lo stesso, la posizione) nella
     * BFS o nelle colonne del tableau) specificata.
     * 
     * @param i
     * @return
     */
    private Variable getVariable(final int i) {
        return variables[i];
    }

    private Variable getVariableByRow(final int r) {
        for (final Variable variable : variables)
            if (variable.rowId == r - 1)
                return variable;
        return null; // Non dovrei mai arrivare qui.
    }

    protected float[] getBFS() {
        for (int i = 0; i < bfs.length; i++) {
            // Se la variabile in esame e' di base, ha valore pari al valore
            // nella colonna
            // dei termini noti del tableau, altrimenti e' pari a 0
            bfs[i] = getVariable(i).isBasic ? tableau.getElement(getVariable(i).rowId + 1, tableau.getNc() - 1) : 0;
            System.out.print(bfs[i] + "\t"); // Per debug
        }
        System.out.println();
        for (int i = 0; i < bfs.length; i++)
            System.out.print(getVariable(i).rowId + "\t"); // Per debug
        System.out.println();
        return bfs;
    }

    protected void getCoefficientBFS(final Coefficient[] extBFS) {
        for (int i = 0; i < extBFS.length; i++)
            extBFS[i].setValue(bfs[i]);
    }

    /**
     * Crea un tableau esterno dal tableau interno
     */
    protected void getCoefficientTableau(final Coefficient[][] oldTableau) {
        final int nr = constraintNumber + 1, nc = oldTableau[1].length;
        // Recupera i coefficienti
        for (int i = 0; i < nr; i++)
            for (int j = 0; j < nc; j++)
                if (Math.abs(tableau.getElement(i, j)) < 0.00001) {
                    oldTableau[i][j].setValue(0);
                    tableau.setElement(i, j, 0);
                } else
                    oldTableau[i][j].setValue(tableau.getElement(i, j));
    }

    /** Esegue il test di ammissibilita' alla fine della Fase Uno. */
    protected boolean isFeasible() {
        if (tableau.getElement(0, solverData.extTableau[0].length - 1) != 0)
            return false;
        return true;
    }

    /**
     * Confronta i tableaux interno ed esterno e la BFS.
     */
    protected boolean isTableauCorrect() {
        boolean flag = true;
        // Se la BFS e' diversa, l'utente ha sbagliato a inserire i dati.
        for (int i = 0; i < bfs.length; i++)
            // Arrotondo eventuali errori di approssimazione.
            if (Math.abs(bfs[i] - solverData.extBFS[i].getCoefficient()) >= 0.01f) {
                solverData.extBFS[i].setBorder(BorderFactory.createEtchedBorder(Color.RED, Color.GRAY));
                Simply.lowerMark();
                flag = false;
            }
        // Se siamo qui, abbiamo superato il test della BFS.
        // Se un elemento e' diverso, le matrici sono diverse
        for (int i = 0; i < tableau.getNr(); i++)
            for (int j = 0; j < tableau.getNc(); j++)
                // Arrotondo eventuali errori di approssimazione.
                if (Math.abs(tableau.getElement(i, j) - solverData.extTableau[i][j].getCoefficient()) >= 0.01f) {
                    solverData.extTableau[i][j].setBorder(BorderFactory.createEtchedBorder(Color.RED, Color.GRAY));
                    Simply.lowerMark();
                    flag = false;
                }
        // Se niente di cui sopra, le matrici sono identiche!
        return flag;
    }

    /**
     * Esegue il test di ottimalita'.
     * 
     * @return <code>true</code> se il test di ottimalita' ha successo,
     *         <code>false</code> altrimenti.
     */
    protected boolean isTableauOptimum() {
        for (int i = 0; i < variablesNumber + slackNumber + surplusNumber + artificialNumber; i++)
            if (tableau.getElement(0, i) < 0)
                return false;
        return true;
    }

    /**
     * Esegue il test di illimitatezza dopo aver trovato una variabile entrante.
     */
    protected boolean isUnbounded() {
        for (int i = 0; i < constraintNumber; i++)
            if (tableau.getElement(i + 1, pvtCol) > 0)
                return false;
        return true;
    }

    /**
     * Ripassa il tableau per controllare se le colonne delle variabili di base
     * sono nel loro giusto stato. Il controllo e' limitato alla riga 0 del
     * tableau, perche' solo qui possono verificarsi discrepanze al di fuori
     * delle operazioni di pivot.
     */
    protected void normalizeTableau() {
        for (int i = 0; i < variables.length; i++)
            if (variables[i].isBasic)
                tableau.addTimesLineToLine(-tableau.getElement(0, i), variables[i].rowId + 1, 0);
        // Imposto i nuovi valori delle variabili
        for (int i = 0; i < variablesNumber + slackNumber + surplusNumber + artificialNumber; i++)
            getVariable(i).setCoefficient(tableau.getElement(0, i));
    }

    /**
     * Esegue operazioni di pivot intorno alla variabile specificata. Sistema
     * anche le caratteristiche delle Variable passate a parametro.
     * 
     * @param pvt
     */
    protected void pivotAround() {
        final Variable entering = this.getVariable(Solver.pvtCol);
        final Variable exiting = this.getVariableByRow(Solver.pvtRow);
        // System.out.println(Solver.pvtRow + " " + Solver.pvtCol);
        // System.out.println("In entrata " + entering.getCoefficient());
        // System.out.println("In uscita " + exiting.getCoefficient());
        // La variabile entrante dev'essere non di base e quella uscente, di
        // base.
        if (entering.isBasic || !exiting.isBasic)
            System.exit(255);
        // Per prima cosa, divido la riga della variabile
        // uscente per il valore pivot
        tableau.divideLineByScalar(tableau.getElement(Solver.pvtRow, Solver.pvtCol), Solver.pvtRow);
        // Dopodiche', aggiorno le altre righe per avere 0
        // nella colonna della variabile entrante
        for (int i = 0; i < constraintNumber + 1; i++)
            if (i != Solver.pvtRow)
                // Aggiungi (o sottrai, somma algebrica comunque) la riga
                // pivot alle altre righe, moltiplicata per l'inverso della
                // quantita' nella colonna pivot: in questo modo, tutte le altre
                // righe (diverse cioe' dalla riga pivot) hanno valori nella
                // colonna pivot pari a 0
                tableau.addTimesLineToLine(-tableau.getElement(i, Solver.pvtCol), Solver.pvtRow, i);
        // Scambio lo stato delle variabili e aggiorno i coefficienti.
        entering.toggleBasic();
        exiting.toggleBasic();
        // Scambio i numeri di riga.
        entering.rowId = exiting.rowId;
        exiting.rowId = -1;
        // Imposto i nuovi valori delle variabili
        for (int i = 0; i < variables.length; i++)
            getVariable(i).setCoefficient(tableau.getElement(0, i));
        // Aggiorno la BFS attuale.
        bfs = getBFS();
    }

    protected void setEntering() {
        float minimum = 0f;
        // Confronto i coefficienti con un minimo posto inizialmente a 0,
        // per avere confronti solo su valori negativi. Se una variabile
        // ha un valore inferiore, diventa il nuovo minimo. Se due variabili
        // hanno lo stesso valore, viene scelta quella con l'indice inferiore.
        for (int i = 0; i < variables.length; i++)
            // System.out.print(variables[i].getCoefficient() + " ");
            if (!variables[i].isBasic
            // Approssimato alla seconda cifra decimale
                    && (variables[i].getCoefficient() - minimum) < -0.01) {
                minimum = variables[i].getCoefficient();
                Solver.pvtCol = i;
            }
    }

    protected void setExiting() {
        // System.out.println();
        float minRatio = Float.POSITIVE_INFINITY, ratio;
        final int lastCol = variablesNumber + slackNumber + surplusNumber + artificialNumber;
        // Per ogni riga, divido il termine noto per il coefficiente nella
        // colonna pivot, solo se entrambi sono maggiori di 0.
        // Il minor risultato della divisione e' scelto.
        for (int i = 0; i < constraintNumber; i++)
            if (tableau.getElement(i + 1, Solver.pvtCol) > 0 && tableau.getElement(i + 1, lastCol) >= 0) {
                ratio = tableau.getElement(i + 1, lastCol) / tableau.getElement(i + 1, Solver.pvtCol);
                // System.out.print(ratio + " ");
                if (ratio < minRatio) {
                    // Se il risultato e' minore di qualche precedente, diventa
                    // il nuovo minimo.
                    minRatio = ratio;
                    Solver.pvtRow = i + 1;
                }
            }
        // System.out.println();
    }

    /**
     * Imposta il tableau interno per l'inizio della fase artificiale 2.
     */
    protected void setInternalPhaseTwoTableau() {
        Matrix newTableau = new Matrix(tableau.getNr(), tableau.getNc() - artificialNumber);
        float[] newBfs = new float[variablesNumber + slackNumber + surplusNumber];
        Variable[] newVars = new Variable[variablesNumber + slackNumber + surplusNumber];
        // Riempio i contenitori
        for (int i = 0; i < newTableau.getNr(); i++) {
            for (int j = 0; j < newTableau.getNc(); j++) {
                newTableau.setElement(i, j, tableau.getElement(i, j));
                // E' il momento di includere i coefficienti della funzione
                // obiettivo.
                if (i == 0 && j < variablesNumber)
                    newTableau.setElement(i, j,
                            (solverData.sourceProblem.getProblemType().equals(Constants.Maximize) ? -1 : 1)
                                    * solverData.sourceProblem.objectiveCoefficientVector[j].getCoefficient());
            }
            // Il ciclo for precedente non considera che l'ultima colonna e'
            // rilevante. Correggo.
            newTableau
                    .setElement(i, tableau.getNc() - artificialNumber - 1, tableau.getElement(i, tableau.getNc() - 1));
        }
        tableau = newTableau;
        for (int i = 0; i < newVars.length; i++)
            // Dovrebbe andare bene cosi' com'e'.
            newVars[i] = variables[i];
        variables = newVars;
        bfs = newBfs; // Imposto la lunghezza
        bfs = getBFS();
        // Imposto le nuove quantita'.
        artificialNumber = 0;
        // Per il GC
        newTableau = null;
        newVars = null;
        newBfs = null;
    }
}
