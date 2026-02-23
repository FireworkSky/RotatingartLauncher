package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData

/**
 * 多边形编辑器入口对话框 - 横屏管理界面
 */
@Composable
fun PolygonEditorDialog(
    currentPoints: List<ControlData.Button.Point>,
    onConfirm: (List<ControlData.Button.Point>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPoints by remember { 
        mutableStateOf(currentPoints.map { Offset(it.x, it.y) })
    }
    
    var showDrawingCanvas by remember { mutableStateOf(false) }
    
    if (showDrawingCanvas) {
        PolygonDrawingCanvas(
            initialPoints = selectedPoints,
            onConfirm = { points ->
                selectedPoints = points
                showDrawingCanvas = false
            },
            onDismiss = { showDrawingCanvas = false }
        )
        return
    }
    
    // 横屏管理界面
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧：形状预览区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedPoints.size >= 3) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { showDrawingCanvas = true }
                        ) {
                            PolygonPreview(
                                points = selectedPoints,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    stringResource(R.string.control_editor_polygon_vertex_count, selectedPoints.size),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    stringResource(R.string.control_editor_polygon_tap_to_edit),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { showDrawingCanvas = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.control_editor_polygon_tap_to_draw),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // 右侧：操作面板
                Surface(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.control_editor_polygon_shape),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, stringResource(R.string.close), modifier = Modifier.size(20.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { showDrawingCanvas = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (selectedPoints.size >= 3) {
                                        stringResource(R.string.control_editor_polygon_redraw)
                                    } else {
                                        stringResource(R.string.control_editor_polygon_draw)
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            
                            if (selectedPoints.size >= 3) {
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                OutlinedButton(
                                    onClick = { selectedPoints = emptyList() },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.control_clear), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        
                        Column {
                            Button(
                                onClick = { 
                                    onConfirm(selectedPoints.map { 
                                        ControlData.Button.Point(it.x, it.y) 
                                    })
                                },
                                enabled = selectedPoints.size >= 3,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.control_editor_apply), style = MaterialTheme.typography.labelLarge)
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 多边形预览组件
 */
@Composable
fun PolygonPreview(
    points: List<Offset>,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    strokeColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        if (points.size < 3) return@Canvas
        
        val path = Path().apply {
            moveTo(points.first().x * size.width, points.first().y * size.height)
            for (i in 1 until points.size) {
                lineTo(points[i].x * size.width, points[i].y * size.height)
            }
            close()
        }
        
        drawPath(path = path, color = fillColor)
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        points.forEach { point ->
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = Offset(point.x * size.width, point.y * size.height)
            )
            drawCircle(
                color = strokeColor,
                radius = 5f,
                center = Offset(point.x * size.width, point.y * size.height)
            )
        }
    }
}

/**
 * 将坐标吸附到网格
 */
fun snapToGrid(value: Float, gridSize: Float, enabled: Boolean): Float {
    if (!enabled) return value
    return (value / gridSize).let { kotlin.math.round(it) } * gridSize
}

/**
 * 多边形绘制画布 - 连续直线绘制模式
 * 每一笔自动变成一条直线，自动连接到上一笔的终点，形成闭合多边形
 * 支持网格吸附和顶点拖动调整
 */
@Composable
fun PolygonDrawingCanvas(
    initialPoints: List<Offset>,
    onConfirm: (List<Offset>) -> Unit,
    onDismiss: () -> Unit
) {
    // 当前正在绘制的轨迹
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    // 顶点列表（按顺序连接）
    var vertices by remember { mutableStateOf(initialPoints) }
    
    // 是否正在绘制新顶点
    var isDrawing by remember { mutableStateOf(false) }
    
    // 是否正在拖动现有顶点
    var draggingVertexIndex by remember { mutableStateOf(-1) }
    
    // 画布尺寸
    var canvasSize by remember { mutableStateOf(Offset(1f, 1f)) }
    
    // 历史记录
    var history by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    
    // 网格吸附开关
    var snapToGridEnabled by remember { mutableStateOf(true) }
    
    // 网格密度 (可调整 4-24格)
    var gridDivisions by remember { mutableStateOf(12) }
    val gridSize = 1f / gridDivisions
    
    // 提取颜色供 Canvas DrawScope 使用
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧：绘制画布
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(12.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .pointerInput(snapToGridEnabled, gridDivisions) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        canvasSize = Offset(size.width.toFloat(), size.height.toFloat())
                                        
                                        // 检查是否点击了现有顶点（用于拖动调整）
                                        val touchRadius = 40f // 触摸半径
                                        val clickedIndex = vertices.indexOfFirst { vertex ->
                                            val vertexPos = Offset(
                                                vertex.x * canvasSize.x,
                                                vertex.y * canvasSize.y
                                            )
                                            (offset - vertexPos).getDistance() < touchRadius
                                        }
                                        
                                        if (clickedIndex >= 0) {
                                            // 拖动现有顶点
                                            draggingVertexIndex = clickedIndex
                                            history = history + listOf(vertices)
                                        } else {
                                            // 绘制新顶点
                                            isDrawing = true
                                            currentStroke = listOf(offset)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (draggingVertexIndex >= 0) {
                                            // 拖动现有顶点
                                            var newX = (change.position.x / canvasSize.x).coerceIn(0f, 1f)
                                            var newY = (change.position.y / canvasSize.y).coerceIn(0f, 1f)
                                            
                                            // 网格吸附
                                            newX = snapToGrid(newX, gridSize, snapToGridEnabled)
                                            newY = snapToGrid(newY, gridSize, snapToGridEnabled)
                                            
                                            vertices = vertices.toMutableList().apply {
                                                this[draggingVertexIndex] = Offset(newX, newY)
                                            }
                                        } else {
                                            // 绘制新轨迹
                                            currentStroke = currentStroke + change.position
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggingVertexIndex >= 0) {
                                            // 结束顶点拖动
                                            draggingVertexIndex = -1
                                        } else {
                                            // 结束新顶点绘制
                                            isDrawing = false
                                            if (currentStroke.size >= 2) {
                                                // 保存历史
                                                history = history + listOf(vertices)
                                                
                                                // 将终点转换为新顶点（带网格吸附）
                                                var endX = (currentStroke.last().x / canvasSize.x).coerceIn(0f, 1f)
                                                var endY = (currentStroke.last().y / canvasSize.y).coerceIn(0f, 1f)
                                                endX = snapToGrid(endX, gridSize, snapToGridEnabled)
                                                endY = snapToGrid(endY, gridSize, snapToGridEnabled)
                                                val endPoint = Offset(endX, endY)
                                                
                                                if (vertices.isEmpty()) {
                                                    // 第一笔：添加起点和终点
                                                    var startX = (currentStroke.first().x / canvasSize.x).coerceIn(0f, 1f)
                                                    var startY = (currentStroke.first().y / canvasSize.y).coerceIn(0f, 1f)
                                                    startX = snapToGrid(startX, gridSize, snapToGridEnabled)
                                                    startY = snapToGrid(startY, gridSize, snapToGridEnabled)
                                                    vertices = listOf(Offset(startX, startY), endPoint)
                                                } else {
                                                    // 后续笔：只添加终点
                                                    vertices = vertices + endPoint
                                                }
                                            }
                                            currentStroke = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        isDrawing = false
                                        draggingVertexIndex = -1
                                        currentStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        // 绘制网格（吸附开启时更明显）
                        val gridColor = if (snapToGridEnabled) 
                            Color.Gray.copy(alpha = 0.25f) 
                        else 
                            Color.Gray.copy(alpha = 0.1f)
                        val centerLineColor = if (snapToGridEnabled)
                            primaryColor.copy(alpha = 0.4f)
                        else
                            Color.Gray.copy(alpha = 0.15f)
                        
                        for (i in 0..gridDivisions) {
                            val x = canvasWidth * i / gridDivisions
                            val y = canvasHeight * i / gridDivisions
                            val isCenterLine = i == gridDivisions / 2
                            val lineColor = if (isCenterLine) centerLineColor else gridColor
                            val strokeW = if (isCenterLine) 2f else 1f
                            
                            drawLine(lineColor, Offset(x, 0f), Offset(x, canvasHeight), strokeWidth = strokeW)
                            drawLine(lineColor, Offset(0f, y), Offset(canvasWidth, y), strokeWidth = strokeW)
                        }
                        
                        // 绘制已有的顶点和连线
                        if (vertices.size >= 2) {
                            // 绘制填充（3个顶点以上才填充）
                            if (vertices.size >= 3) {
                                val fillPath = Path().apply {
                                    val first = vertices.first()
                                    moveTo(first.x * canvasWidth, first.y * canvasHeight)
                                    for (i in 1 until vertices.size) {
                                        lineTo(vertices[i].x * canvasWidth, vertices[i].y * canvasHeight)
                                    }
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    color = primaryColor.copy(alpha = 0.2f)
                                )
                            }
                            
                            // 绘制边线
                            for (i in 0 until vertices.size) {
                                val start = vertices[i]
                                val end = vertices[(i + 1) % vertices.size]
                                
                                // 最后一条边（闭合边）用虚线表示
                                val isClosingEdge = i == vertices.size - 1
                                
                                drawLine(
                                    color = if (isClosingEdge) primaryColor.copy(alpha = 0.5f) else primaryColor,
                                    start = Offset(start.x * canvasWidth, start.y * canvasHeight),
                                    end = Offset(end.x * canvasWidth, end.y * canvasHeight),
                                    strokeWidth = if (isClosingEdge) 2f else 4f,
                                    cap = StrokeCap.Round
                                )
                            }
                            
                            // 绘制顶点
                            vertices.forEachIndexed { index, point ->
                                val isFirst = index == 0
                                val isLast = index == vertices.size - 1
                                val isDragging = index == draggingVertexIndex
                                
                                // 拖动时显示更大的圆圈
                                val radius = if (isDragging) 16f else 10f
                                val innerRadius = if (isDragging) 12f else 7f
                                
                                drawCircle(
                                    color = Color.White,
                                    radius = radius,
                                    center = Offset(point.x * canvasWidth, point.y * canvasHeight)
                                )
                                drawCircle(
                                    color = when {
                                        isDragging -> secondaryColor // 粉色拖动中
                                        isFirst -> tertiaryColor // 绿色起点
                                        isLast -> errorColor  // 橙色终点
                                        else -> primaryColor
                                    },
                                    radius = innerRadius,
                                    center = Offset(point.x * canvasWidth, point.y * canvasHeight)
                                )
                            }
                        }
                        
                        // 绘制当前正在画的轨迹（显示为虚拟直线预览）
                        if (currentStroke.size >= 2) {
                            val end = currentStroke.last()
                            
                            // 计算起点：如果已有顶点，从最后一个顶点开始；否则从手绘起点开始
                            val start = if (vertices.isNotEmpty()) {
                                Offset(
                                    vertices.last().x * canvasWidth,
                                    vertices.last().y * canvasHeight
                                )
                            } else {
                                currentStroke.first()
                            }
                            
                            // 显示实际手绘轨迹（淡色）
                            val strokePath = Path().apply {
                                moveTo(currentStroke.first().x, currentStroke.first().y)
                                currentStroke.forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = strokePath,
                                color = errorColor.copy(alpha = 0.3f),
                                style = Stroke(width = 2f, cap = StrokeCap.Round)
                            )
                            
                            // 显示将要生成的直线（从上一个顶点/起点到当前终点）
                            drawLine(
                                color = errorColor,
                                start = start,
                                end = end,
                                strokeWidth = 5f,
                                cap = StrokeCap.Round
                            )
                            
                            // 绘制起点标记
                            drawCircle(
                                color = tertiaryColor,
                                radius = 12f,
                                center = start
                            )
                            // 绘制终点标记
                            drawCircle(
                                color = errorColor,
                                radius = 14f,
                                center = end
                            )
                        }
                    }
                    
                    // 顶点数量提示
                    if (vertices.isNotEmpty() && !isDrawing) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                stringResource(R.string.control_editor_polygon_vertex_count, vertices.size),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    
                    // 空白提示
                    if (vertices.isEmpty() && !isDrawing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.control_editor_polygon_draw_shape),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.control_editor_polygon_stroke_add_vertex),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.control_editor_polygon_auto_connect_last_vertex),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                    
                    // 提示继续绘制
                    if (vertices.isNotEmpty() && !isDrawing && vertices.size < 3) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                        ) {
                            Text(
                                stringResource(R.string.control_editor_polygon_continue_drawing),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                
                // 右侧：工具面板
                Surface(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // 标题栏
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                                }
                                Text(stringResource(R.string.control_editor_polygon_draw), style = MaterialTheme.typography.titleMedium)
                                IconButton(
                                    onClick = {
                                        if (history.isNotEmpty()) {
                                            vertices = history.last()
                                            history = history.dropLast(1)
                                        }
                                    },
                                    enabled = history.isNotEmpty(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        stringResource(R.string.control_editor_undo),
                                        tint = if (history.isNotEmpty()) 
                                            MaterialTheme.colorScheme.onSurface 
                                        else 
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 网格吸附开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.control_editor_grid_snap),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Switch(
                                    checked = snapToGridEnabled,
                                    onCheckedChange = { snapToGridEnabled = it },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            
                            // 网格密度滑块
                            if (snapToGridEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.control_editor_grid_density, gridDivisions),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = gridDivisions.toFloat(),
                                    onValueChange = { gridDivisions = it.toInt() },
                                    valueRange = 4f..24f,
                                    steps = 19, // 4,5,6...24 = 20个值，19个间隔
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 使用说明
                            Text(
                                stringResource(R.string.control_editor_instructions),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.control_editor_polygon_instruction_lines),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 清空按钮
                            OutlinedButton(
                                onClick = {
                                    history = history + listOf(vertices)
                                    vertices = emptyList()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = vertices.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.control_editor_clear_all))
                            }
                        }
                        
                        // 确认按钮
                        Button(
                            onClick = { 
                                onConfirm(vertices)
                            },
                            enabled = vertices.size >= 3,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.control_editor_done))
                        }
                    }
                }
            }
        }
    }
}
