/*
Copyright 2019 Naoki Matsumoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import chisel3._
import chisel3.util.{BitPat, Cat}

class ExUnitPort(implicit val conf:CAHPConfig) extends Bundle {
  val in = Input(new ExUnitIn)
  val memIn = Input(new MemUnitIn)
  val wbIn = Input(new WbUnitIn)
  val enable = Input(Bool())
  val flush = Input(Bool())

  val out = new ExUnitOut
  val memOut = Output(new MemUnitIn)
  val wbOut = Output(new WbUnitIn)
}

class ExUnitIn(implicit val conf:CAHPConfig) extends Bundle {
  val instAALU = new ALUPortIn
  val instBALU = new ALUPortIn

  // instA => false, instB => true
  val selJump = Bool()
  val selMem = Bool()

  val bcIn = new BranchControllerIn()
}

class BranchControllerIn(implicit val conf:CAHPConfig) extends Bundle {
  val pcOpcode = UInt(3.W)
  val pc = UInt(16.W)
  val pcImm = UInt(16.W)
  val pcAdd = Bool()
}

class ExUnitOut(implicit val conf:CAHPConfig) extends Bundle {
  val instARes = Output(UInt(16.W))
  val instBRes = Output(UInt(16.W))
  val jumpAddress = Output(UInt(conf.romAddrWidth.W))
  val jump = Output(Bool())
}

class ExUnit(implicit val conf:CAHPConfig) extends Module {
  val io = IO(new ExUnitPort)
  val instAALU = Module(new ALU)
  val instBALU = Module(new ALU)
  val pExReg = RegInit(0.U.asTypeOf(new ExUnitIn))
  val pMemReg = RegInit(0.U.asTypeOf(new MemUnitIn))
  val pWbReg = RegInit(0.U.asTypeOf(new WbUnitIn))


  when(io.enable) {
    pExReg := io.in
    pMemReg := io.memIn
    pWbReg := io.wbIn
    when(io.flush){
      pMemReg.memWrite := false.B
      pWbReg.finishFlag := false.B
      pWbReg.instARegWrite.regWriteEnable := false.B
      pWbReg.instBRegWrite.regWriteEnable := false.B
      pExReg.bcIn.pcOpcode := 0.U
      //when(io.wbIn.instARegWrite.regWriteEnable){
      //  printf("Disable regWrite x%d\n", io.wbIn.instARegWrite.regWrite)
      //}
      //when(io.wbIn.instBRegWrite.regWriteEnable){
      //  printf("Disable regWrite x%d\n", io.wbIn.instBRegWrite.regWrite)
      //}
    }
  }

  instAALU.io.in := pExReg.instAALU
  io.out.instARes := instAALU.io.out.out

  instBALU.io.in := pExReg.instBALU
  io.out.instBRes := instBALU.io.out.out

  io.memOut := pMemReg
  when(!pExReg.selMem){
    io.memOut.address := io.out.instARes
  }.otherwise{
    io.memOut.address := io.out.instBRes
  }

  io.wbOut := pWbReg
  io.wbOut.instARegWrite.regWriteData := io.out.instARes
  io.wbOut.instBRegWrite.regWriteData := io.out.instBRes

  when(pExReg.bcIn.pcAdd) {
    io.out.jumpAddress := pExReg.bcIn.pc + pExReg.bcIn.pcImm
  }.otherwise{
    io.out.jumpAddress := pExReg.bcIn.pcImm
  }

  val flagCarry = Wire(Bool())
  val flagOverflow = Wire(Bool())
  val flagSign = Wire(Bool())
  val flagZero = Wire(Bool())
  when(!pExReg.selJump){
    flagCarry := instAALU.io.out.flagCarry
    flagOverflow := instAALU.io.out.flagOverflow
    flagSign := instAALU.io.out.flagSign
    flagZero := instAALU.io.out.flagZero
  }.otherwise{
    flagCarry := instBALU.io.out.flagCarry
    flagOverflow := instBALU.io.out.flagOverflow
    flagSign := instBALU.io.out.flagSign
    flagZero := instBALU.io.out.flagZero
  }

  io.out.jump := false.B
  when(pExReg.bcIn.pcOpcode === 1.U){
    io.out.jump := flagZero
  }.elsewhen(pExReg.bcIn.pcOpcode === 2.U){
    io.out.jump := flagCarry
  }.elsewhen(pExReg.bcIn.pcOpcode === 3.U){
    io.out.jump := flagCarry||flagZero
  }.elsewhen(pExReg.bcIn.pcOpcode === 4.U){
    io.out.jump := true.B
  }.elsewhen(pExReg.bcIn.pcOpcode === 5.U){
    io.out.jump := !flagZero
  }.elsewhen(pExReg.bcIn.pcOpcode === 6.U){
    io.out.jump := flagSign != flagOverflow
  }.elsewhen(pExReg.bcIn.pcOpcode === 7.U){
    io.out.jump := (flagSign != flagOverflow)||flagZero
  }
  //printf("[EX] FLAGS Carry:%d Sign:%d Zero:%d OverFlow:%d\n", flagCarry, flagSign, flagZero, flagOverflow)

  when(conf.debugEx.B) {
    //printf("[EX] opcode:0x%x\n", pExReg.opcode)
    //printf("[EX] inA:0x%x\n", pExReg.inA)
    //printf("[EX] inB:0x%x\n", pExReg.inB)
    //printf("[EX] Res:0x%x\n", io.out.res)
    //printf("[EX] PC Address:0x%x\n", pExReg.pc)
    printf("[EX] Jump:%d\n", io.out.jump)
    printf("[EX] JumpAddress:0x%x\n", io.out.jumpAddress)
  }
}
object ALUOpcode {
  def ADD = BitPat("b0000")
  def SUB = BitPat("b0001")
  def AND = BitPat("b0010")
  def XOR = BitPat("b0011")
  def OR  = BitPat("b0100")
  def LSL = BitPat("b0101")
  def LSR = BitPat("b0110")
  def ASR = BitPat("b0111")
  def MOV = BitPat("b1000")
}
