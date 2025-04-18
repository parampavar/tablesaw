package tech.tablesaw.io;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.columns.AbstractColumnParser;

public class ColumnTypeDetector {

  private final List<ColumnType> typeArray;

  /**
   * @param typeArray Types to choose from. When more than one would work, we pick the first of the
   *     options. The order these appear in is critical. The broadest must go last, so String must
   *     be at the end of the list. Any String read from the input will match string. If it were
   *     first on the list, you would get nothing but strings in your table. As another example, an
   *     integer type, should go before double. Otherwise double would match integers so the integer
   *     test would never be evaluated and all the ints would be read as doubles.
   */
  public ColumnTypeDetector(List<ColumnType> typeArray) {
    this.typeArray = typeArray;
  }

  /**
   * Estimates and returns the type for each column in the input text
   *
   * <p>The type is determined by checking a sample of the data. Because only a sample of the data
   * is checked, the types may be incorrect. If that is the case a Parse Exception will be thrown.
   *
   * <p>The method {@code printColumnTypes()} can be used to print a list of the detected columns
   * that can be corrected and used to explicitly specify the correct column types.
   */
  public ColumnType[] detectColumnTypes(Iterator<String[]> rows, ReadOptions options) {
    boolean useSampling = options.sample();

    // to hold the results
    List<ColumnType> columnTypes = new ArrayList<>();

    // to hold the data read from the file
    List<List<String>> columnData = new ArrayList<>();

    int rowCount = 0; // make sure we don't go over maxRows

    int nextRow = 0;
    String[] nextLine = new String[0];
    while (rows.hasNext()) {
      try {
        nextLine = rows.next();
        // initialize the arrays to hold the strings. we don't know how many we need until we read
        // the
        // first row
        if (rowCount == 0) {
          for (int i = 0; i < nextLine.length; i++) {
            columnData.add(new ArrayList<>());
          }
        }
        int columnNumber = 0;
        if (rowCount == nextRow) {
          for (String field : nextLine) {
            columnData.get(columnNumber).add(field);
            columnNumber++;
          }
          if (useSampling) {
            nextRow = nextRow(nextRow);
          } else {
            nextRow = nextRowWithoutSampling(nextRow);
          }
        }
        rowCount++;
      } catch (IndexOutOfBoundsException e) {
        throw new ColumnIndexOutOfBoundsException(e, nextRow, nextLine);
      }
    }

    // now detect
    for (List<String> valuesList : columnData) {
      ColumnType detectedType = detectType(valuesList, options);
      /*
            if (detectedType.equals(STRING) && rowCount > STRING_COLUMN_ROW_COUNT_CUTOFF
              && options.columnTypesToDetect().contains(TEXT)
            ) {
              HashSet<String> unique = new HashSet<>(valuesList);
              double uniquePct = unique.size() / (valuesList.size() * 1.0);
              if (uniquePct > STRING_COLUMN_CUTOFF) {
                detectedType = TEXT;
              }
            }
      */
      columnTypes.add(detectedType);
    }
    return columnTypes.toArray(new ColumnType[0]);
  }

  private int nextRowWithoutSampling(int nextRow) {
    return nextRow + 1;
  }

  private int nextRow(int nextRow) {
    if (nextRow < 10_000) {
      return nextRow + 1;
    }
    if (nextRow < 100_000) {
      return nextRow + 1000;
    }
    if (nextRow < 1_000_000) {
      return nextRow + 10_000;
    }
    if (nextRow < 10_000_000) {
      return nextRow + 100_000;
    }
    if (nextRow < 100_000_000) {
      return nextRow + 1_000_000;
    }
    return nextRow + 10_000_000;
  }

  /**
   * Returns a predicted ColumnType derived by analyzing the given list of undifferentiated strings
   * read from a column in the file and applying the given Locale and options
   */
  private ColumnType detectType(List<String> valuesList, ReadOptions options) {

    CopyOnWriteArrayList<AbstractColumnParser<?>> parsers =
        new CopyOnWriteArrayList<>(getParserList(typeArray, options));

    CopyOnWriteArrayList<ColumnType> typeCandidates = new CopyOnWriteArrayList<>(typeArray);

    boolean hasNonMissingValues = false;

    for (String s : valuesList) {
      for (AbstractColumnParser<?> parser : parsers) {
        if (!parser.isMissing(s)) {
          hasNonMissingValues = true;
          if (!parser.canParse(s)) { // we can skip this test if we know the value is missing
            typeCandidates.remove(parser.columnType());
            parsers.remove(parser);
          }
        }
      }
    }
    if (hasNonMissingValues) {
      return selectType(typeCandidates);
    } else {
      // the last type in the typeArray is the default
      return typeArray.get(typeArray.size() - 1);
    }
  }

  /**
   * Returns the selected candidate for a column of data, by picking the first value in the given
   * list
   *
   * @param typeCandidates a possibly empty list of candidates. This list should be sorted in order
   *     of preference
   */
  private ColumnType selectType(List<ColumnType> typeCandidates) {
    return typeCandidates.get(0);
  }

  /**
   * Returns the list of parsers to use for type detection
   *
   * @param typeArray Array of column types. The order specifies the order the types are applied
   * @param options CsvReadOptions to use to modify the default parsers for each type
   * @return A list of parsers in the order they should be used for type detection
   */
  private List<AbstractColumnParser<?>> getParserList(
      List<ColumnType> typeArray, ReadOptions options) {
    // Types to choose from. When more than one would work, we pick the first of the options

    List<AbstractColumnParser<?>> parsers = new ArrayList<>();
    for (ColumnType type : typeArray) {
      parsers.add(type.customParser(options));
    }
    return parsers;
  }
}
