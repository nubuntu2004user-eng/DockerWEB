package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.Font


@Composable
fun rightPanelContainers(withToFill: Float, font: Font){
    val secondaryColor = Color(50, 74, 110)
    var userInputSearch by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxHeight().fillMaxWidth(withToFill).background(Color(27, 45, 68)).padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.07f).padding(4.dp)) {

            Text("Containers" , fontFamily = font.font , color = font.fontColor , fontSize = 32.sp)

            Spacer(modifier = Modifier.fillMaxWidth(0.3f))

            OutlinedTextField(value = userInputSearch , onValueChange = {userInputSearch = it} , placeholder = {Text("Search by name", fontFamily = font.font , color = font.fontColor)} ,
                modifier = Modifier.fillMaxWidth(0.6f),
                shape = RoundedCornerShape(20),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    focusedContainerColor = Color(27, 45, 68),
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color(27, 45, 68)
                )
                )

            Spacer(modifier = Modifier.fillMaxWidth(0.05f))

            ElevatedButton(onClick = {} , colors = ButtonDefaults.buttonColors(
                containerColor = secondaryColor,
                contentColor = Color.White
            )){
                Text("Prune stopped" , fontFamily = font.font , color = font.fontColor)
            }

            Spacer(modifier = Modifier.fillMaxWidth(0.05f))

            ElevatedButton(onClick = {} , colors = ButtonDefaults.buttonColors(
                containerColor = Color(31, 111, 235),
                contentColor = Color.White
            )){
                Text("+ Run new container" , fontFamily = font.font , color = font.fontColor)
            }

        }

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.25f).padding(4.dp)) {
            ElevatedCard(modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp) , colors = CardDefaults.cardColors(
                containerColor = secondaryColor
            )) {
                Text("Running\n3" , color = font.fontColor , fontFamily = font.font , fontSize = 48.sp , modifier = Modifier.padding(8.dp))
            }
            ElevatedCard(modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp) , colors = CardDefaults.cardColors(
                containerColor = secondaryColor
            )) {
                Text("Stopped\n2" , color = font.fontColor , fontFamily = font.font, fontSize = 48.sp , modifier = Modifier.padding(8.dp))
            }
            ElevatedCard(modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp) , colors = CardDefaults.cardColors(
                containerColor = secondaryColor
            )) {
                Text("HostCpu\n5" , color = font.fontColor , fontFamily = font.font, fontSize = 48.sp , modifier = Modifier.padding(8.dp))
            }
            ElevatedCard(modifier = Modifier.weight(0.2f).fillMaxHeight().padding(8.dp) , colors = CardDefaults.cardColors(
                containerColor = secondaryColor
            )) {
                Text("HostRam\n99" , color = font.fontColor , fontFamily = font.font, fontSize = 48.sp , modifier = Modifier.padding(8.dp))
            }
        }

    }
}