package com.example.posedetectionapp.usecase.recordings

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.posedetectionapp.utils.Constant
import com.example.posedetectionapp.R
import com.example.posedetectionapp.adapters.RecyclerViewAdapter
import kotlinx.android.synthetic.main.fragment_all_recordings.*
import java.io.File
import java.util.*

class AllRecordingsFragment : Fragment() {

    private lateinit var recyclerViewAdapter: RecyclerViewAdapter
    private lateinit var storage: File
    private lateinit var fileToDelete: File
    private var positionToDelete: Int = 0


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.fragment_all_recordings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    private fun init() {
        Constant.allMediaList.clear()
        recyclerViewFiles.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewFiles.setHasFixedSize(true)
        recyclerViewFiles.setItemViewCacheSize(20)
        recyclerViewFiles.isNestedScrollingEnabled = false
        recyclerViewAdapter = RecyclerViewAdapter(requireContext())
        recyclerViewFiles.adapter = recyclerViewAdapter

        recyclerViewAdapter.onMoreOptionsClick = { position, fileToDelete, view ->
            positionToDelete = position
            this.fileToDelete = fileToDelete
            //showOptionsPopup(view)
            deleteConfirmationDialog()
        }
        val directory = "${activity?.filesDir}/PoseDetection"
        storage = File(directory)
        loadDirectoryFiles(storage)
        recyclerViewAdapter.notifyDataSetChanged()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val directory = "${activity?.filesDir}/PoseDetection"
                storage = File(directory)
                loadDirectoryFiles(storage)
                recyclerViewAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun loadDirectoryFiles(directory: File) {
        val fileList = directory.listFiles()
        if (fileList != null && fileList.isNotEmpty()) {
            for (i in fileList.indices) {
                if (fileList[i].isDirectory) {
                    loadDirectoryFiles(fileList[i])
                } else {
                    val name = fileList[i].name.toLowerCase(Locale.ROOT)
                    for (extension in Constant.videoExtensions) {
                        if (name.endsWith(extension)) {
                            Constant.allMediaList.add(fileList[i])
                            break
                        }
                    }
                }
            }
        }
    }


    private fun deleteConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.DialogTheme)
        builder.setTitle("Delete Recording")
                .setMessage("Are you sure you want to delete video?")
                .setPositiveButton("Yes") { dialog, _ ->
                    if (fileToDelete.exists()) {
                        recyclerViewAdapter.removeElement(positionToDelete)
                        fileToDelete.delete()
                        Toast.makeText(requireContext(), "Video deleted.", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
        builder.create().show()
    }

}