package com.ospdf.reader.domain.usecase

import com.ospdf.reader.domain.model.InkStroke

/**
 * Command pattern interface for undo/redo operations.
 */
sealed interface UndoableAction {
    fun undo(strokes: MutableList<InkStroke>)
    fun redo(strokes: MutableList<InkStroke>)
}

/**
 * Action: Adding a new stroke.
 */
data class AddStrokeAction(val stroke: InkStroke) : UndoableAction {
    override fun undo(strokes: MutableList<InkStroke>) {
        strokes.removeAll { it.id == stroke.id }
    }
    
    override fun redo(strokes: MutableList<InkStroke>) {
        strokes.add(stroke)
    }
}

/**
 * Action: Removing a stroke (eraser).
 */
data class RemoveStrokeAction(val stroke: InkStroke, val index: Int) : UndoableAction {
    override fun undo(strokes: MutableList<InkStroke>) {
        strokes.add(index.coerceIn(0, strokes.size), stroke)
    }
    
    override fun redo(strokes: MutableList<InkStroke>) {
        strokes.removeAll { it.id == stroke.id }
    }
}

/**
 * Action: Multiple actions grouped together.
 */
data class BatchAction(val actions: List<UndoableAction>) : UndoableAction {
    override fun undo(strokes: MutableList<InkStroke>) {
        // Undo in reverse order
        actions.reversed().forEach { it.undo(strokes) }
    }
    
    override fun redo(strokes: MutableList<InkStroke>) {
        actions.forEach { it.redo(strokes) }
    }
}

/**
 * Manages undo/redo stack for annotation actions.
 * Supports unlimited undo levels with configurable max history.
 */
class UndoRedoManager(
    private val maxHistorySize: Int = 100
) {
    private val undoStack = mutableListOf<UndoableAction>()
    private val redoStack = mutableListOf<UndoableAction>()
    
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    
    /**
     * Executes an action and adds it to the undo stack.
     */
    fun execute(action: UndoableAction, strokes: MutableList<InkStroke>) {
        action.redo(strokes)
        undoStack.add(action)
        
        // Clear redo stack when new action is performed
        redoStack.clear()
        
        // Limit history size
        while (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
    }
    
    /**
     * Undoes the last action.
     */
    fun undo(strokes: MutableList<InkStroke>): Boolean {
        if (undoStack.isEmpty()) return false
        
        val action = undoStack.removeLast()
        action.undo(strokes)
        redoStack.add(action)
        
        return true
    }
    
    /**
     * Redoes the last undone action.
     */
    fun redo(strokes: MutableList<InkStroke>): Boolean {
        if (redoStack.isEmpty()) return false
        
        val action = redoStack.removeLast()
        action.redo(strokes)
        undoStack.add(action)
        
        return true
    }
    
    /**
     * Clears all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
    
    /**
     * Returns the number of actions in the undo stack.
     */
    fun undoCount(): Int = undoStack.size
    
    /**
     * Returns the number of actions in the redo stack.
     */
    fun redoCount(): Int = redoStack.size
}
