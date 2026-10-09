package org.sableos.reader.engine.comic

/**
 * Natural, case-insensitive page-name ordering (`page2` before `page10`). The implementation is shared with the other
 * engines in :reader-model so every format orders names identically.
 */
object NaturalOrder : Comparator<String> by org.sableos.reader.model.NaturalOrder
