// FolderPickerDialog.kt
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File

class FolderPickerDialog : DialogFragment() {
    private var currentPath: String = ""
    private var onFolderSelectedListener: ((String) -> Unit)? = null
    private lateinit var adapter: FolderAdapter
    private lateinit var recyclerView: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
        
        // Initialize with external storage directory
        currentPath = requireContext().getExternalFilesDir(null)?.parentFile?.absolutePath 
            ?: "/storage/emulated/0"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_folder_picker, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView(view)
        setupButtons(view)
        updateCurrentPath(currentPath)
    }
    
    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        adapter = FolderAdapter(
            onFolderClick = { folder -> updateCurrentPath(folder.absolutePath) },
            onFolderSelect = { folder ->
                onFolderSelectedListener?.invoke(folder.absolutePath)
                dismiss()
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FolderPickerDialog.adapter
        }
    }
    
    private fun setupButtons(view: View) {
        // Back button
        view.findViewById<MaterialButton>(R.id.buttonBack)?.setOnClickListener {
            val parentFile = File(currentPath).parentFile
            if (parentFile != null) {
                updateCurrentPath(parentFile.absolutePath)
            }
        }
        
        // Cancel button
        view.findViewById<MaterialButton>(R.id.buttonCancel)?.setOnClickListener {
            dismiss()
        }
    }
    
    private fun updateCurrentPath(path: String) {
        currentPath = path
        val currentDir = File(path)
        
        if (!currentDir.exists() || !currentDir.canRead()) {
            Toast.makeText(context, "Cannot access this folder", Toast.LENGTH_SHORT).show()
            return
        }
        
        val folders = currentDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()
            
        adapter.submitList(folders)
        
        // Update current path display
        view?.findViewById<TextView>(R.id.textCurrentPath)?.text = path
    }
    
    fun setOnFolderSelectedListener(listener: (String) -> Unit) {
        onFolderSelectedListener = listener
    }
    
    private class FolderAdapter(
        private val onFolderClick: (File) -> Unit,
        private val onFolderSelect: (File) -> Unit
    ) : ListAdapter<File, FolderAdapter.ViewHolder>(FolderDiffCallback()) {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val folder = getItem(position)
            holder.bind(folder)
        }
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val folderName: TextView = itemView.findViewById(R.id.folderName)
            private val selectButton: MaterialButton = itemView.findViewById(R.id.buttonSelect)
            
            fun bind(folder: File) {
                folderName.text = folder.name
                
                itemView.setOnClickListener { onFolderClick(folder) }
                selectButton.setOnClickListener { onFolderSelect(folder) }
            }
        }
    }
    
    private class FolderDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }
        
        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath &&
                   oldItem.lastModified() == newItem.lastModified()
        }
    }
}
